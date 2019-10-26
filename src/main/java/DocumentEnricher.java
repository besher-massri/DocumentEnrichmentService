import org.json.JSONObject;

public interface DocumentEnricher {
    JSONObject process(String id, String text);
}
