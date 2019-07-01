import io.datakernel.serializer.annotations.Deserialize;
import io.datakernel.serializer.annotations.Serialize;
import io.datakernel.serializer.annotations.SerializeNullable;

// [START EXAMPLE]
public class GetResponse {
	private final String value;

	public GetResponse(@Deserialize("value") String value) {
		this.value = value;
	}

	@Serialize(order = 0)
	@SerializeNullable
	public String getValue() {
		return value;
	}

	@Override
	public String toString() {
		return "{value='" + value + '\'' + '}';
	}
}
// [END EXAMPLE]
