import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.io.*;
import java.util.*;

public class ConsumerAgent extends BrokerClientAgent {
    private String[] buyableComponents;
    private Double balance;
    private List<CarComponent> ownedCarComponents;

    protected void setup() {
        System.out.println("Consumer agent " + getLocalName() + " has started...");
        this.balance = 10000.0;
        this.buyableComponents = new String[]{CarComponentType.ALTERNATOR.name(),
                                              CarComponentType.CAMSHAFT.name(),
                                              CarComponentType.CLUTCH_PLATE.name()};
        this.ownedCarComponents = new ArrayList<>();

        ParallelBehaviour priceRequestAndRequestHandlerBehaviour = new ParallelBehaviour();
        priceRequestAndRequestHandlerBehaviour.addSubBehaviour(createConsumerPriceRequestAndRespondBehaviour());
        priceRequestAndRequestHandlerBehaviour.addSubBehaviour(createRequestHandler());

        SequentialBehaviour initializationBehaviour = new SequentialBehaviour();
        initializationBehaviour.addSubBehaviour(createBrokerSearchBehaviour());
        initializationBehaviour.addSubBehaviour(priceRequestAndRequestHandlerBehaviour);

        addBehaviour(initializationBehaviour);
    }

    private void payMoney(Double price) {
        this.balance -= price;
    }

    private Behaviour createBrokerSearchBehaviour() {
        return new BrokerSearchBehaviour(this, 2000);
    }

    private String pickRandomComponentType() {
        int minimum = 0;
        int maximum = this.buyableComponents.length - 1;
        int range = maximum - minimum + 1;
        return this.buyableComponents[(int)(Math.random() * range) + minimum];
    }

    private Behaviour createConsumerPriceRequestAndRespondBehaviour() {
        return new TickerBehaviour(this, 5000) {
            @Override
            protected void onTick() {
                String componentTypeAsString = pickRandomComponentType();
                ACLMessage priceRequest = new ACLMessage(ACLMessage.CFP);
                priceRequest.addReceiver(brokerAgents[0]);
                priceRequest.setContent(componentTypeAsString);
                send(priceRequest);
                System.out.println("Consumer " + getLocalName() + " sent a call-for-proposal price request for " + componentTypeAsString + " to the broker.");
            }
        };
    }

    private void receiveBrokerPrices(ACLMessage message) throws IOException, ClassNotFoundException {
        List<PriceInformation> deserializedListOfPrices = Base64Serializer.deserialize(message.getContent());
        String componentTypeAsString = deserializedListOfPrices.get(0).getType().name();

        System.out.println("Consumer " + getLocalName() + " received " + componentTypeAsString + " price proposals from the broker.");

        try {
            Optional<PriceInformation> minimumPriceOptional = deserializedListOfPrices.stream()
                    .min(Comparator.comparingDouble(PriceInformation::getPrice));
            PriceInformation minimumPrice = minimumPriceOptional.get();
            payMoney(minimumPrice.getPrice());
            System.out.println("Consumer " + getLocalName() + " pays for the " + componentTypeAsString + ".");

            PriceInformation offerAcceptPriceInformation = new PriceInformation(minimumPrice.getSupplier(), minimumPrice.getPrice(), minimumPrice.getType());
            offerAcceptPriceInformation.setDestinationAid(this.getAID());
            String serializedAcceptedOffer = Base64Serializer.serialize(offerAcceptPriceInformation);

            ACLMessage componentRequest = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
            componentRequest.addReceiver(brokerAgents[0]);
            componentRequest.setContent(serializedAcceptedOffer);
            send(componentRequest);

            System.out.println("Consumer " + getLocalName() + " sent " + componentTypeAsString + " offer accept message to broker " + brokerAgents[0].getLocalName());

            MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.AGREE);
            ACLMessage boughtProductAsAclMessage = blockingReceive(template);
            ComponentDeliveryUnit deliveryUnitMessage = Base64Serializer.deserialize(boughtProductAsAclMessage.getContent());

            ownedCarComponents.add(deliveryUnitMessage.getComponent());
            System.out.println("Consumer " + getLocalName() + " received bought " + deliveryUnitMessage.getComponent().type() + " sent by " + deliveryUnitMessage.getSource().getLocalName() + " through broker " + brokerAgents[0].getLocalName());
            System.out.println(deliveryUnitMessage.getComponent());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void handleUnknownMessage(ACLMessage message) {
        System.out.println("Consumer " + getLocalName() + " got an unknown message from " + message.getSender().getLocalName() + " with performative " + ACLMessage.getPerformative(message.getPerformative()));
        ACLMessage response = new ACLMessage(ACLMessage.NOT_UNDERSTOOD);
        response.addReceiver(message.getSender());
        send(response);
    }

    protected void handleRequest(ACLMessage request) throws IOException, ClassNotFoundException {
        switch (request.getPerformative()) {
            case ACLMessage.PROPOSE -> receiveBrokerPrices(request);
            default -> handleUnknownMessage(request);
        }
    }

    private Behaviour createRequestHandler() {
        return new RequestHandlingBehaviour();
    }

    protected void takeDown() {
        System.out.println("Consumer " + getAID().getName() + " has terminated.");
    }
}
