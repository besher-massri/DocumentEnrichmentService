import io.github.cdimascio.dotenv.Dotenv;
import spark.ResponseTransformer;

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
}
public class APIController {
    private static Boolean getBoolean(String val){
        if (val==null){
            return null;
        }
        return Boolean.valueOf(val);
    }
    public static void main(String[] args) {
        Dotenv dotenv =  Dotenv.configure()
                .directory("config/")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();
        int port=Integer.parseInt(Objects.requireNonNull(dotenv.get("PORT")));
        port(port);
        DocumentAnnotatorMicroservice APIService=new DocumentAnnotatorMicroservice();

        after((req, res) -> res.type("application/json"));
        exception(IllegalArgumentException.class, (e, req, res) -> {
            res.status(400);
            res.body(JsonUtil.toJson(new ResponseError(e)));
        });
        post("/annotate", (req, res) -> APIService.annotateDocument(
                req.queryParams("id"),
                req.queryParams("text"),
                getBoolean(req.queryParams("NER")),
                getBoolean(req.queryParams("wordAnnotations")),
                getBoolean(req.queryParams("synonyms")),
                getBoolean(req.queryParams("splitIntoParagraphs")),
                getBoolean(req.queryParams("indices")),
                getBoolean(req.queryParams("spaces")),
                getBoolean(req.queryParams("wikiConcepts")),
                req.queryParams("ontology"),
                getBoolean(req.queryParams("allowAlternativeNames")),
                getBoolean(req.queryParams("hierarchy"))
        ));
    }
}

