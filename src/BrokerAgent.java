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
        initializationBehaviour.addSubBehaviour(createSupplierRegistrationListeningBehaviour());
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

    private Behaviour createSupplierRegistrationListeningBehaviour() {
        return new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate messageTemplate = MessageTemplate.MatchPerformative(ACLMessage.SUBSCRIBE);
                ACLMessage supplierRegistration = myAgent.receive(messageTemplate);
                if (supplierRegistration != null) {
                    suppliers.add(supplierRegistration.getSender());
                    System.out.println("Broker " + getLocalName() + " registered supplier " + supplierRegistration.getSender().getLocalName());
                    System.out.println("Broker " + getLocalName() + " received registrations from these suppliers:");
                    Stream.of(suppliers).forEach(System.out::println);
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
