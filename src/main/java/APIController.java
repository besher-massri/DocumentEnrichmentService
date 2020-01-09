import io.github.cdimascio.dotenv.Dotenv;
import org.json.JSONObject;
import spark.ResponseTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static spark.Spark.*;
import static spark.Spark.post;

class JsonUtil {
    public static String toJson(Object object) {
        return object.toString();
        //return new Gson().toJson(object);
    }

    public static ResponseTransformer json() {
        return JsonUtil::toJson;
    }
}

class ResponseError {
    private String message;

    public ResponseError(String message, String... args) {
        this.message = String.format(message, args);
    }

    ResponseError(Exception e) {
        this.message = e.getMessage();
    }

    public String getMessage() {
        return this.message;
    }

    public String toString() {
        return "{error:" + message + "}";
    }
}

public class APIController {
    private static Boolean getBoolean(String val) {
        if (val == null) {
            return null;
        }
        return Boolean.valueOf(val);
    }

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure()
                .directory("config/")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();
        int port = Integer.parseInt(Objects.requireNonNull(dotenv.get("PORT")));
        port(port);
        DocumentAnnotatorMicroservice APIService = new DocumentAnnotatorMicroservice();

        after((req, res) -> res.type("application/json"));
        exception(IllegalArgumentException.class, (e, req, res) -> {
            res.status(400);
            res.body(JsonUtil.toJson(new ResponseError(e)));
        });
        post("/annotate", (req, res) -> {
            String id = req.queryParams("id");
            Boolean NER = getBoolean(req.queryParams("NER"));
            Boolean wordAnnotations = getBoolean(req.queryParams("wordAnnotations"));
            Boolean synonyms = getBoolean(req.queryParams("synonyms"));
            Boolean splitIntoParagraphs = getBoolean(req.queryParams("splitIntoParagraphs"));
            Boolean numericClassifiers = getBoolean(req.queryParams("numericClassifiers"));
            Boolean indices = getBoolean(req.queryParams("indices"));
            Boolean spaces = getBoolean(req.queryParams("spaces"));
            Boolean wikiConcepts = getBoolean(req.queryParams("wikiConcepts"));
            String ontology = req.queryParams("ontology");
            Boolean allowAlternativeNames = getBoolean(req.queryParams("allowAlternativeNames"));
            Boolean hierarchy = getBoolean(req.queryParams("hierarchy"));
            List<String> texts = new ArrayList<>();
            List<String> languages = new ArrayList<>();
            String[] langArr = req.queryParamsValues("languages");
            for (String lang : langArr) {
                languages.add(lang);
                try {
                    String text = req.queryParams("text_" + lang);
                    if (text == null) {
                        throw new Exception();
                    }
                    texts.add(text);
                } catch (Exception e) {
                    JSONObject error = new JSONObject();
                    return new ResponseError("\"text_" + lang + "\" field is missing. Text for language \"" + lang + "\" is required");
                }
            }
            return APIService.annotateDocument(
                    id,
                    texts,
                    languages,
                    NER,
                    wordAnnotations,
                    synonyms,
                    splitIntoParagraphs,
                    numericClassifiers,
                    indices,
                    spaces,
                    wikiConcepts,
                    ontology,
                    allowAlternativeNames,
                    hierarchy
            );
        });
    }
}

