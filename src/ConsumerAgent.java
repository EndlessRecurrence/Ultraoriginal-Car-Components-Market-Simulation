import jade.core.Agent;

public class ConsumerAgent extends Agent {
    protected void setup() {
        System.out.println("Consumer agent " + getLocalName() + " has started...");
    }
}
