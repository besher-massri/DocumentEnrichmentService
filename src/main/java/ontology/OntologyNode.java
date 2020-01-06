package ontology;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class OntologyNode {
    private String id;
    private String name;
    private List<String> alternativeNames;
    private List<String> definitions;
    private List<String> categories;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getAlternativeNames() {
        return alternativeNames;
    }

    public List<String> getDefinitions() {
        return definitions;
    }

    public List<String> getCategories() {
        return categories;
    }

    public OntologyNode(String id, String name, List<String> alternativeNames, List<String> definitions, List<String> categories) {
        this.id = id;
        this.name = name;
        this.alternativeNames = alternativeNames;
        this.definitions = definitions;
        this.categories = categories;
    }

    private void readArrayField(String name, JSONObject obj, List<String> arr) {
        if (obj.has(name)) {
            JSONArray jsonArr = obj.getJSONArray(name);
            for (Object jsonItem : jsonArr) {
                String strItem = (String) jsonItem;
                arr.add(strItem);
            }
        }
    }

    public OntologyNode(JSONObject obj) throws NoSuchFieldException, JSONException {
        if (!obj.has("id")) {
            throw new NoSuchFieldException("id");
        }
        this.id = obj.getString("id");
        if (!obj.has("name")) {
            throw new NoSuchFieldException("name");
        }
        this.name = obj.getString("name");
        this.alternativeNames = new ArrayList<>();
        this.categories = new ArrayList<>();
        this.definitions = new ArrayList<>();
        readArrayField("alternative_names", obj, this.alternativeNames);
        readArrayField("categories", obj, this.categories);
        readArrayField("definitions", obj, this.definitions);
    }
}
