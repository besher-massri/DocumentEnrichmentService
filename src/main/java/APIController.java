import spark.ResponseTransformer;

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
        int port=4322;
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
                getBoolean(req.queryParams("spaces"))
        ));
    }
}

