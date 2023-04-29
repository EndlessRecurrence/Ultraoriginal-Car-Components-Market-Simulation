import jade.core.*;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.lang.acl.MessageTemplate;
import jade.util.Logger;

import java.awt.image.AreaAveragingScaleFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

public class BrokerAgent extends Agent {
    private List<AID> suppliers;
    private Map<AID, List<PriceInformation>> aidPriceMap;

    protected void setup() {
        System.out.println("Broker agent " + getLocalName() + " has started...");
        this.suppliers = new ArrayList<>();
        this.aidPriceMap = new HashMap<>();
        SequentialBehaviour initializationBehaviour = new SequentialBehaviour();
        initializationBehaviour.addSubBehaviour(createDfServiceRegistrationBehaviour());
        initializationBehaviour.addSubBehaviour(createRequestHandlingBehaviour());
        addBehaviour(initializationBehaviour);
    }

    private Behaviour createDfServiceRegistrationBehaviour() {
        return new OneShotBehaviour() {
            @Override
            public void action() {
                DFAgentDescription dfd = new DFAgentDescription();
                dfd.setName(getAID());
                ServiceDescription sd = new ServiceDescription();
                sd.setType("car-components-trading-brokership");
                sd.setName("car-components-broker");
                dfd.addServices(sd);
                try {
                    DFService.register(myAgent, dfd);
                    System.out.println("Broker " + getLocalName() + " registered in the DfService yellow pages.");
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                    System.out.println("Broker " + getLocalName() + " failed to register in the DfService yellow pages.");
                }
            }
        };
    }

    private void handleSupplierSubscriptionRequest(ACLMessage request) {
        suppliers.add(request.getSender());
        System.out.println("Broker " + getLocalName() + " registered supplier " + request.getSender().getLocalName());
        System.out.println("Broker " + getLocalName() + " received registrations from these suppliers:");
        Stream.of(suppliers).forEach(System.out::println);
    }

    private void handleConsumerPriceRequest(ACLMessage request) throws IOException {
        System.out.println("Broker " + getLocalName() + " got a call-for-proposal price request from " + request.getSender().getLocalName() + " for a " + request.getContent() + " component.");
        ACLMessage priceRequest = new ACLMessage(ACLMessage.REQUEST);
        String componentTypeAsString = request.getContent();
        CarComponentType type = CarComponentType.valueOf(componentTypeAsString);
        aidPriceMap.put(request.getSender(), new ArrayList<>());

        PriceInformation price = new PriceInformation(null, null, type);
        price.setDestinationAid(request.getSender());
        String serializedObjectString = Base64Serializer.serialize(price);

        priceRequest.setContent(serializedObjectString);
        suppliers.forEach(priceRequest::addReceiver);
        send(priceRequest);
    }

    private void handleSupplierPriceInform(ACLMessage request) throws IOException, ClassNotFoundException {
        System.out.println("Broker " + getLocalName() + " received the following serialized supplier price inform message from " + request.getSender().getLocalName());
        System.out.println(request.getContent());

        PriceInformation supplierPriceInformation = Base64Serializer.deserialize(request.getContent());

        List<PriceInformation> prices = aidPriceMap.get(supplierPriceInformation.getDestinationAid());
        prices.add(supplierPriceInformation);
        aidPriceMap.put(supplierPriceInformation.getDestinationAid(), prices);

        if (prices.size() == suppliers.size()) {
            ACLMessage pricesToSendToConsumer = new ACLMessage(ACLMessage.PROPOSE);
            String serializedPrices = Base64Serializer.serialize(prices);
            pricesToSendToConsumer.addReceiver(supplierPriceInformation.getDestinationAid());
            pricesToSendToConsumer.setContent(serializedPrices);
            send(pricesToSendToConsumer);
        }
    }

    private void handleAcceptedPriceProposal(ACLMessage request) throws IOException, ClassNotFoundException {
        System.out.println("Broker " + getLocalName() + " received an accepted price proposal answer from " + request.getSender().getLocalName());

        PriceInformation priceProposalAcceptMessage = Base64Serializer.deserialize(request.getContent());

        ACLMessage productRetrievalRequestMessage = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
        productRetrievalRequestMessage.addReceiver(priceProposalAcceptMessage.getSupplier());
        productRetrievalRequestMessage.setContent(request.getContent());
        send(productRetrievalRequestMessage);
    }

    private void handleDeliveryUnitFromSupplier(ACLMessage message) throws IOException, ClassNotFoundException {
        System.out.println("Broker " + getLocalName() + " received delivery unit from supplier " + message.getSender().getLocalName());
        ComponentDeliveryUnit deliveryUnitMessage = Base64Serializer.deserialize(message.getContent());

        message.removeReceiver(this.getAID());
        message.addReceiver(deliveryUnitMessage.getDestination());
        message.setPerformative(ACLMessage.AGREE);
        send(message);
        System.out.println("Broker " + getLocalName() + " sent delivery unit from supplier " + message.getSender().getLocalName() + " to consumer " + deliveryUnitMessage.getDestination().getLocalName());
    }

    private void handleUnknownRequestMessage(ACLMessage request) {
        System.out.println("Broker " + getLocalName() + " got an unknown message from " + request.getSender().getLocalName() + " with performative " + ACLMessage.getPerformative(request.getPerformative()));
        ACLMessage response = new ACLMessage(ACLMessage.NOT_UNDERSTOOD);
        response.addReceiver(request.getSender());
        send(response);
    }

    private void handleRequest(ACLMessage request) throws IOException, ClassNotFoundException {
        switch (request.getPerformative()) {
            case ACLMessage.SUBSCRIBE -> handleSupplierSubscriptionRequest(request);
            case ACLMessage.CFP -> handleConsumerPriceRequest(request);
            case ACLMessage.INFORM -> handleSupplierPriceInform(request);
            case ACLMessage.ACCEPT_PROPOSAL -> handleAcceptedPriceProposal(request);
            case ACLMessage.AGREE -> handleDeliveryUnitFromSupplier(request);
            default -> handleUnknownRequestMessage(request);
        }
    }

    private Behaviour createRequestHandlingBehaviour() {
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
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        System.out.println("Broker " + getAID().getName() + " has terminated.");
    }
}
