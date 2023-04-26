import jade.core.*;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.util.Logger;

import java.util.stream.Stream;

public class SupplierAgent extends Agent {
    private AID[] brokerAgents;

    protected void setup() {
        System.out.println("Supplier agent " + getLocalName() + " has started...");
        SequentialBehaviour initializationBehaviour = new SequentialBehaviour();
        initializationBehaviour.addSubBehaviour(createBrokerSearchBehaviour());
        initializationBehaviour.addSubBehaviour(createBrokerRegistrationBehaviour());
        addBehaviour(initializationBehaviour);
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

    private Behaviour createBrokerRegistrationBehaviour() {
        return new WakerBehaviour(this, 2000) {
            @Override
            protected void onWake() {
                ACLMessage registrationMessage = new ACLMessage(ACLMessage.SUBSCRIBE);
                registrationMessage.addReceiver(brokerAgents[0]);
                registrationMessage.setContent("supplier_registration");
                send(registrationMessage);
                System.out.println("Supplier " + getLocalName() + " has sent registration to the broker.");
            }
        };
    }

    protected void takeDown() {
        System.out.println("Supplier " + getAID().getName() + " has terminated.");
    }
}
