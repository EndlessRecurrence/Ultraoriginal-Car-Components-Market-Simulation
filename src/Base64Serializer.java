import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Base64Serializer {
    public static <T> String serialize(T entity) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        ObjectOutputStream objectOut = new ObjectOutputStream(byteOut);
        objectOut.writeObject(entity);
        objectOut.flush();
        byte[] serializedObject = byteOut.toByteArray();
        objectOut.close();
        byteOut.close();
        return Base64.getEncoder().encodeToString(serializedObject);
    }

    public static <T> T deserialize(String serializedEntity) throws IOException, ClassNotFoundException {
        byte[] serializedObjectBytes = Base64.getDecoder().decode(serializedEntity.getBytes(StandardCharsets.UTF_8));
        ByteArrayInputStream byteIn = new ByteArrayInputStream(serializedObjectBytes);
        ObjectInputStream objectIn = new ObjectInputStream(byteIn);
        T deserializedEntity = (T) objectIn.readObject();
        objectIn.close();
        byteIn.close();

        return deserializedEntity;
    }
}
