import jade.core.AID;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import java.util.stream.Stream;

public class BrokerSearchBehaviour extends WakerBehaviour {

    public BrokerSearchBehaviour(BrokerClientAgent a, long timeout) {
        super(a, timeout);
    }

    @Override
    protected void onWake() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription serviceDescription = new ServiceDescription();
        serviceDescription.setType("car-components-trading-brokership");
        template.addServices(serviceDescription);
        try {
            DFAgentDescription[] result = DFService.search(myAgent, template);
            AID[] brokerAgents = new AID[result.length];
            for (int i = 0; i < result.length; ++i) {
                brokerAgents[i] = result[i].getName();
            }
            ((BrokerClientAgent) myAgent).setBrokerAgents(brokerAgents);
            System.out.println("Agents detected by " + myAgent.getLocalName() + ":");
            Stream.of(brokerAgents).forEach(System.out::println);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }
}
