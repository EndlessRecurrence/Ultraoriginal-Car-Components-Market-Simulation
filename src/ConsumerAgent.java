import jade.core.*;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.util.Logger;

import java.util.stream.Stream;

public class ConsumerAgent extends Agent {
    private AID[] brokerAgents;

    protected void setup() {
        System.out.println("Consumer agent " + getLocalName() + " has started...");
        SequentialBehaviour initializationBehaviour = new SequentialBehaviour();
        initializationBehaviour.addSubBehaviour(createBrokerSearchBehaviour());
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

    protected void takeDown() {
        System.out.println("Consumer " + getAID().getName() + " has terminated.");
    }
}
