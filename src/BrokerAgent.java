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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
        System.out.println("Broker " + getLocalName() + " got a call-for-proposal price request from " + request.getSender().getLocalName());
    }

    private void handleUnknownRequestMessage(ACLMessage request) {
        ACLMessage response = new ACLMessage(ACLMessage.NOT_UNDERSTOOD);
        response.addReceiver(request.getSender());
        send(response);
    }

    private void handleRequest(ACLMessage request) {
        switch (request.getPerformative()) {
            case ACLMessage.SUBSCRIBE -> handleSupplierSubscriptionRequest(request);
            case ACLMessage.CFP -> handleConsumerPriceRequest(request);
            default -> handleUnknownRequestMessage(request);
        }
    }

    private Behaviour createRequestHandlingBehaviour() {
        return new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage message = myAgent.receive();
                if (message != null) {
                    handleRequest(message);
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
