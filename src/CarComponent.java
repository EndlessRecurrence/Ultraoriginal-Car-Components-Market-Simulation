import java.io.Serializable;

public record CarComponent(CarComponentType type, Double price) implements Serializable {}
