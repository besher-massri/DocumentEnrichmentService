import org.json.JSONObject;

import java.util.List;

public interface DocumentEnricher {
    JSONObject process(String id, List<String> text, List<String> language);
}
