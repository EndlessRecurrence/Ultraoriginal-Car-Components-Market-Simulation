import jade.core.*;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.util.AccessControlList;
import jade.util.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.Base64;

public class SupplierAgent extends Agent {
    private AID[] brokerAgents;
    private Map<CarComponentType, Integer> stock;
    private Double balance;
    public static Map<CarComponentType, Double> referenceComponentPrices;
    private Map<CarComponentType, Double> prices;

    protected void setup() {
        System.out.println("Supplier agent " + getLocalName() + " has started...");

        referenceComponentPrices = initializeReferenceComponentPrices();
        this.prices = initializeComponentPrices();
        this.stock = initializeStock();
        this.balance = 0.0;

        SequentialBehaviour initializationBehaviour = new SequentialBehaviour();
        initializationBehaviour.addSubBehaviour(createBrokerSearchBehaviour());
        initializationBehaviour.addSubBehaviour(createBrokerRegistrationBehaviour());
        ParallelBehaviour requestHandlingAndPriceUpdateBehaviour = new ParallelBehaviour();
        requestHandlingAndPriceUpdateBehaviour.addSubBehaviour(createRequestHandlingBehaviour());
        requestHandlingAndPriceUpdateBehaviour.addSubBehaviour(createPriceUpdateBehaviour());
        initializationBehaviour.addSubBehaviour(requestHandlingAndPriceUpdateBehaviour);

        addBehaviour(initializationBehaviour);
    }

    public static Map<CarComponentType, Double> initializeReferenceComponentPrices() {
        Map<CarComponentType, Double> prices = new HashMap<>();
        prices.put(CarComponentType.CAMSHAFT, 60.0);
        prices.put(CarComponentType.CLUTCH_PLATE, 80.0);
        prices.put(CarComponentType.ALTERNATOR, 250.0);
        return prices;
    }

    private Map<CarComponentType, Double> initializeComponentPrices() {
        Map<CarComponentType, Double> prices = new HashMap<>();
        prices.put(CarComponentType.CAMSHAFT, 60.0);
        prices.put(CarComponentType.CLUTCH_PLATE, 80.0);
        prices.put(CarComponentType.ALTERNATOR, 250.0);
        return prices;
    }

    private Map<CarComponentType, Integer> initializeStock() {
        Map<CarComponentType, Integer> stock = new HashMap<>();
        stock.put(CarComponentType.CAMSHAFT, 100);
        stock.put(CarComponentType.CLUTCH_PLATE, 100);
        stock.put(CarComponentType.ALTERNATOR, 100);
        return stock;
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

    /**
     *  Generates a random price for a car component based on the hardcoded reference prices.
     *  The price update is usually an update that is of a variation percentage between 0 and 20%.
     *  If the generated price falls below or above the chosen thresholds (i.e. if the update exceeds
     *  or falls under 50% of the reference price, the updated price is set to the corresponding threshold).
     *
     *  Let's assume that the reference price for an engine block is 500.0$.
     *  For example, if an engine block currently costs 500.0$, the random number for the variation
     *  percentage (which is between 0 and 1) is 0.5 and the variation sign is -1.0 (in other words,
     *  negative), then the final result of the updated price will be:
     *       500.0 + (-1.0) * 0.5 * 0.2 * 500.0 = 450.0$
     *   which represents a variation of -10% on the previous price.
     *
     *   In another example, we have another engine block that currently costs 300.0$, the random number for the
     *   variation percentage is 1.0, which multiplied with 0.2 returns 0.2 representing a 20% variation for the price.
     *   The variation sign is -1.0, so the result is:
     *      300.0 + (-1.0) * 1.0 * 0.2 * 300.0 = 240.0
     *   But 240.0 is less than the lower threshold for the price, which is half of the reference price of 500.0,
     *   i.e. 250.0. So, the new price will actually be set to 250.0$
     * **/
    public static Double generateUpdatedPrice(CarComponentType type, Double price) {
        Double sign = (Math.random() < 0.5 ? -1.0 : 1.0);
        Double percentage = Math.random() * 0.2;
        Double newPrice = price + sign * percentage * price;
        Double lowerThreshold = referenceComponentPrices.get(type) - 0.5 * referenceComponentPrices.get(type);
        Double upperThreshold = referenceComponentPrices.get(type) + 0.5 * referenceComponentPrices.get(type);

        if (newPrice < lowerThreshold) {
            newPrice = lowerThreshold;
        } else if (newPrice > upperThreshold) {
            newPrice = upperThreshold;
        }

        return newPrice;
    }

    private Behaviour createPriceUpdateBehaviour() {
        return new TickerBehaviour(this, 5000) {
            @Override
            protected void onTick() {
                referenceComponentPrices.entrySet().stream()
                        .map(typePricePair -> new CarComponent(typePricePair.getKey(), generateUpdatedPrice(typePricePair.getKey(), typePricePair.getValue())))
                        .forEach(carComponent -> prices.put(carComponent.type(), carComponent.price()));
                String listOfUpdatedPricesAsString = Stream.of(prices)
                        .map(Object::toString)
                        .reduce("", (acc, x) -> acc + x);
                System.out.println(getLocalName() + "'s randomly updated prices: " + listOfUpdatedPricesAsString);
            }
        };
    }

    private void handleUnknownRequestMessage(ACLMessage request) throws IOException {
        System.out.println("Supplier " + getLocalName() + " received a message that is not understood.");
        ACLMessage response = new ACLMessage(ACLMessage.NOT_UNDERSTOOD);
        response.addReceiver(request.getSender());
        send(response);
        throw new IOException("Message " + request.getPerformative() + " with content " + request.getContent() + "and performative " + ACLMessage.getPerformative(request.getPerformative()) + " not understood.");
    }

    private void handleBrokerPriceRequest(ACLMessage request) throws IOException, ClassNotFoundException {
        PriceInformation priceRequestFromBroker = Base64Serializer.deserialize(request.getContent());

        String componentTypeAsString = priceRequestFromBroker.getType().name();
        System.out.println("Supplier " + getLocalName() + " received price request from " + brokerAgents[0].getLocalName() + " for " + componentTypeAsString);

        PriceInformation price = new PriceInformation(this.getAID(), prices.get(priceRequestFromBroker.getType()), priceRequestFromBroker.getType());
        price.setDestinationAid(priceRequestFromBroker.getDestinationAid());
        String serializedObjectString = Base64Serializer.serialize(price);

        ACLMessage priceMessage = new ACLMessage(ACLMessage.INFORM);
        priceMessage.addReceiver(brokerAgents[0]);
        priceMessage.setContent(serializedObjectString);
        send(priceMessage);
        System.out.println("Supplier " + getLocalName() + " sent " + componentTypeAsString + " price information to " + brokerAgents[0].getLocalName());
    }

    private void handleComponentRequestFromBroker(ACLMessage message) throws IOException, ClassNotFoundException {
        System.out.println("Supplier " + getLocalName() + " received a component request from " + brokerAgents[0].getLocalName());
        PriceInformation componentRequestFromBroker = Base64Serializer.deserialize(message.getContent());

        this.balance += componentRequestFromBroker.getPrice();
        Integer currentStock = this.stock.get(componentRequestFromBroker.getType());
        this.stock.put(componentRequestFromBroker.getType(), currentStock - 1);

        ComponentDeliveryUnit unit = new ComponentDeliveryUnit(new CarComponent(componentRequestFromBroker.getType(), componentRequestFromBroker.getPrice()), this.getAID(), componentRequestFromBroker.getDestinationAid());
        String serializedDeliveryUnit = Base64Serializer.serialize(unit);

        ACLMessage deliveryUnitResponseToBroker = new ACLMessage(ACLMessage.AGREE);
        deliveryUnitResponseToBroker.addReceiver(brokerAgents[0]);
        deliveryUnitResponseToBroker.setContent(serializedDeliveryUnit);
        send(deliveryUnitResponseToBroker);
        System.out.println("Supplier " + getLocalName() + " sent the requested component to " + brokerAgents[0].getLocalName());
    }

    private void handleRequest(ACLMessage request) throws IOException, ClassNotFoundException {
        switch (request.getPerformative()) {
            case ACLMessage.REQUEST -> handleBrokerPriceRequest(request);
            case ACLMessage.ACCEPT_PROPOSAL -> handleComponentRequestFromBroker(request);
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
        System.out.println("Supplier " + getAID().getName() + " has terminated.");
    }
}
