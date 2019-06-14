import io.datakernel.serializer.annotations.Deserialize;
import io.datakernel.serializer.annotations.Serialize;

// [START EXAMPLE]
public class PutRequest {

	private final String key;
	private final String value;

	public PutRequest(@Deserialize("key") String key, @Deserialize("value") String value) {
		this.key = key;
		this.value = value;
	}

	@Serialize(order = 0)
	public String getKey() {
		return key;
	}

	@Serialize(order = 1)
	public String getValue() {
		return value;
	}
}
// [END EXAMPLE]
