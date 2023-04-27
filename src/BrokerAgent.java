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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.Base64;

public class BrokerAgent extends Agent {
    private List<AID> suppliers;

    protected void setup() {
        System.out.println("Broker agent " + getLocalName() + " has started...");
        this.suppliers = new ArrayList<>();
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

    private void handleConsumerPriceRequest(ACLMessage request) {
        System.out.println("Broker " + getLocalName() + " got a call-for-proposal price request from " + request.getSender().getLocalName() + " for a " + request.getContent() + " component.");
        ACLMessage priceRequest = new ACLMessage(ACLMessage.REQUEST);
        priceRequest.setContent(request.getContent());
        suppliers.forEach(supplier -> priceRequest.addReceiver(supplier));
        send(priceRequest);
    }

    private void handleSupplierPriceInform(ACLMessage request) throws IOException, ClassNotFoundException {
        System.out.println("Broker " + getLocalName() + " received the following serialized supplier price inform message from " + request.getSender().getLocalName());
        System.out.println(request.getContent());
        byte[] serializedObjectBytes = Base64.getDecoder().decode(request.getContent().getBytes(StandardCharsets.UTF_8));
        ByteArrayInputStream byteIn = new ByteArrayInputStream(serializedObjectBytes);
        ObjectInputStream objectIn = new ObjectInputStream(byteIn);
        PriceInformation supplierPriceInformation = (PriceInformation) objectIn.readObject();
        objectIn.close();
        byteIn.close();
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
