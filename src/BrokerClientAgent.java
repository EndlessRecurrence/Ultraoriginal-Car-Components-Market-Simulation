import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;

import java.io.IOException;

public abstract class BrokerClientAgent extends Agent {
    protected AID[] brokerAgents;

    public void setBrokerAgents(AID[] brokerAgents) {
        this.brokerAgents = brokerAgents;
    }

    protected abstract void handleRequest(ACLMessage request) throws IOException, ClassNotFoundException;
}
