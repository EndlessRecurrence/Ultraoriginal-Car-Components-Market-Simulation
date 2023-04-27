import jade.core.*;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.lang.acl.MessageTemplate;
import jade.util.Logger;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;

public class ConsumerAgent extends Agent {
    private AID[] brokerAgents;
    private String[] buyableComponents;
    private Double balance;
    private List<CarComponent> ownedCarComponents;

    protected void setup() {
        System.out.println("Consumer agent " + getLocalName() + " has started...");
        this.balance = 10000.0;
        this.buyableComponents = new String[]{CarComponentType.ALTERNATOR.name(),
                                              CarComponentType.CAMSHAFT.name(),
                                              CarComponentType.CLUTCH_PLATE.name()};
        this.ownedCarComponents = new ArrayList<>();
        SequentialBehaviour initializationBehaviour = new SequentialBehaviour();
        initializationBehaviour.addSubBehaviour(createBrokerSearchBehaviour());
        initializationBehaviour.addSubBehaviour(createConsumerPriceRequestAndRespondBehaviour());
        addBehaviour(initializationBehaviour);
    }

    private void payMoney(Double price) {
        this.balance -= price;
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

    private String pickRandomComponentType() {
        int minimum = 0;
        int maximum = this.buyableComponents.length - 1;
        int range = maximum - minimum + 1;
        return this.buyableComponents[(int)(Math.random() * range) + minimum];
    }

    private Behaviour createConsumerPriceRequestAndRespondBehaviour() {
        return new TickerBehaviour(this, 10000) {
            @Override
            protected void onTick() {
                String componentTypeAsString = pickRandomComponentType();
                ACLMessage priceRequest = new ACLMessage(ACLMessage.CFP);
                priceRequest.addReceiver(brokerAgents[0]);
                priceRequest.setContent(componentTypeAsString);
                send(priceRequest);
                System.out.println("Consumer " + getLocalName() + " sent a call-for-proposal price request for " + componentTypeAsString + " to the broker.");

                MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
                ACLMessage receivedComponentPricesMessage = blockingReceive(template);
                System.out.println("Consumer " + getLocalName() + " received " + componentTypeAsString + " price proposals from the broker.");

                try {
                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(receivedComponentPricesMessage.getContent().getBytes()));
                    List<PriceInformation> deserializedListOfPrices = (List<PriceInformation>) ois.readObject();
                    Optional<PriceInformation> minimumPriceOptional = deserializedListOfPrices.stream()
                            .min(Comparator.comparingDouble(PriceInformation::getPrice));
                    PriceInformation minimumPrice = minimumPriceOptional.get();
                    payMoney(minimumPrice.getPrice());
                    System.out.println("Consumer " + getLocalName() + " pays for the " + componentTypeAsString + ".");

                    PriceInformation offerAcceptMessage = new PriceInformation(minimumPrice.getSupplier(), minimumPrice.getPrice(), minimumPrice.getType());
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(baos);
                    oos.writeObject(offerAcceptMessage);
                    String serializedOfferAcceptMessageAsString = baos.toString();

                    ACLMessage componentRequest = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    priceRequest.addReceiver(brokerAgents[0]);
                    priceRequest.setContent(serializedOfferAcceptMessageAsString);
                    send(componentRequest);
                    System.out.println("Consumer " + getLocalName() + " sent " + componentTypeAsString + " offer accept message to broker " + brokerAgents[0].getLocalName());

                    template = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                    ACLMessage boughtProductAsAclMessage = blockingReceive(template);
                    System.out.println("Consumer " + getLocalName() + " received bought " + componentTypeAsString + " from broker " + brokerAgents[0].getLocalName());
                    ois = new ObjectInputStream(new ByteArrayInputStream(boughtProductAsAclMessage.getContent().getBytes()));
                    CarComponent boughtProduct = (CarComponent) ois.readObject();
                    ownedCarComponents.add(boughtProduct);
                    System.out.println("Consumer " + getLocalName() + " owns " + ownedCarComponents.size() + " car components.");
                    if (ownedCarComponents.size() >= 100) {
                        System.out.println("Supplier " + getLocalName() + " has stopped requesting components.");
                        stop();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    protected void takeDown() {
        System.out.println("Consumer " + getAID().getName() + " has terminated.");
    }
}
