import jade.core.Agent;

public class BrokerAgent extends Agent {
    protected void setup() {
        System.out.println("Broker agent " + getLocalName() + " has started...");
    }
}
