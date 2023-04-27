import jade.core.*;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.lang.acl.MessageTemplate;
import jade.tools.sniffer.Message;
import jade.util.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

public class ConsumerAgent extends Agent {
    private AID[] brokerAgents;
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
        return new WakerBehaviour(this, 2000) {
            @Override
            protected void onWake() {
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription serviceDescription = new ServiceDescription();
                serviceDescription.setType("car-components-trading-brokership");
                template.addServices(serviceDescription);
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    brokerAgents = new AID[result.length];
                    for (int i = 0; i < result.length; ++i) {
                        brokerAgents[i] = result[i].getName();
                    }
                    System.out.println("Agents detected by " + getLocalName() + ":");
                    Stream.of(brokerAgents).forEach(System.out::println);
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }
            }
        };
    }

    private String pickRandomComponentType() {
        int minimum = 0;
        int maximum = this.buyableComponents.length - 1;
        int range = maximum - minimum + 1;
        return this.buyableComponents[(int)(Math.random() * range) + minimum];
    }

    private Behaviour createConsumerPriceRequestAndRespondBehaviour() {
        return new TickerBehaviour(this, 10000) {
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
        byte[] serializedObjectBytes = Base64.getDecoder().decode(message.getContent().getBytes(StandardCharsets.UTF_8));
        ByteArrayInputStream byteIn = new ByteArrayInputStream(serializedObjectBytes);
        ObjectInputStream objectIn = new ObjectInputStream(byteIn);
        List<PriceInformation> deserializedListOfPrices = (ArrayList<PriceInformation>) objectIn.readObject();
        objectIn.close();
        byteIn.close();

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

            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            ObjectOutputStream objectOut = new ObjectOutputStream(byteOut);
            objectOut.writeObject(offerAcceptPriceInformation);
            objectOut.flush();
            byte[] serializedObject = byteOut.toByteArray();
            objectOut.close();
            byteOut.close();
            String serializedAcceptedOffer = Base64.getEncoder().encodeToString(serializedObject);

            ACLMessage componentRequest = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
            componentRequest.addReceiver(brokerAgents[0]);
            componentRequest.setContent(serializedAcceptedOffer);
            send(componentRequest);

            System.out.println("Consumer " + getLocalName() + " sent " + componentTypeAsString + " offer accept message to broker " + brokerAgents[0].getLocalName());

            MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage boughtProductAsAclMessage = blockingReceive(template);
            System.out.println("Consumer " + getLocalName() + " received bought " + componentTypeAsString + " from broker " + brokerAgents[0].getLocalName());

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

    private void handleRequest(ACLMessage request) throws IOException, ClassNotFoundException {
        switch (request.getPerformative()) {
            case ACLMessage.PROPOSE -> receiveBrokerPrices(request);
            default -> handleUnknownMessage(request);
        }
    }

    private Behaviour createRequestHandler() {
        return new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage message = myAgent.receive();
                if (message != null) {
                    try {
                        handleRequest(message);
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                } else {
                    block();
                }
            }
        };
    }

    protected void takeDown() {
        System.out.println("Consumer " + getAID().getName() + " has terminated.");
    }
}
