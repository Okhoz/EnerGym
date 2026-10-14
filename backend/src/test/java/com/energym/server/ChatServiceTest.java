package com.energym.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Pruebas del contrato real: el único sustituto es el proveedor externo que podría facturar. */
class ChatServiceTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String CODE = "local-pairing-example-32-characters";
    static JsonNode request() throws Exception {
        return JSON.readTree("""
          {"conversationId":"conversation-1","requestId":"request-1","exerciseId":"bodyweight-squat",
           "message":"Explica este ejercicio","historyConsent":false,"history":[],"profile":{
           "sportId":"strength","goal":"STRENGTH","environment":"HOME","focus":"SPECIFIC",
           "category":"NO_EQUIPMENT","resources":[],"daysPerWeek":3,"durationMinutes":30,
           "level":"Principiante","experience":"","space":"","preferences":"","limitations":""}}
          """);
    }
    static JsonNode answer() throws Exception { return JSON.readTree("""
        {"text":"Ejemplo sin revisión profesional.","missingFields":[],"proposal":null,"commands":[]}
        """); }
    SessionTokenStore tokens() { return new SessionTokenStore(CODE, Clock.systemUTC(), Duration.ofHours(1), 10); }
    ChatService service(SessionTokenStore t, Provider p) { return new ChatService(t,p,new ProposalValidator()); }
    @Test void noTokenNeverReachesProvider() throws Exception {
        assertEquals(401,service(tokens(),r->{throw new AssertionError("No debe enviar");}).chat(null,request()).status());
    }
    @Test void pairingIsOneUseAndRevocationRejectsToken() {
        SessionTokenStore t=tokens(); String token=t.pair(CODE).token();
        assertTrue(t.authenticated(token)); assertNull(t.pair(CODE));
        t.revoke(token); assertFalse(t.authenticated(token));
    }
    @Test void tokenExpiresAtDeadline() {
        Clock c=Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"),ZoneOffset.UTC);
        SessionTokenStore t=new SessionTokenStore(CODE,c,Duration.ZERO,10);
        assertFalse(t.authenticated(t.pair(CODE).token()));
    }
    @Test void largeMessageAndUnknownFieldsRejected() throws Exception {
        SessionTokenStore t=tokens(); String token=t.pair(CODE).token(); ChatService s=service(t,r->answer());
        ObjectNode req=(ObjectNode)request(); req.put("message","x".repeat(2001)); assertEquals(400,s.chat(token,req).status());
        req=(ObjectNode)request(); req.put("apiKey","secret"); assertEquals(400,s.chat(token,req).status());
    }
    @Test void unknownExerciseNeverReachesProvider() throws Exception {
        SessionTokenStore t=tokens();ObjectNode req=(ObjectNode)request(); req.put("exerciseId","inventado");
        assertEquals(400,service(t,r->{throw new AssertionError();}).chat(t.pair(CODE).token(),req).status());
    }
    @Test void unauthorisedHistoryIsRemovedBeforeProvider() throws Exception {
        SessionTokenStore t=tokens(); AtomicReference<JsonNode> sent=new AtomicReference<>();
        ObjectNode req=(ObjectNode)request();req.withArray("history").addObject().put("role","user").put("text","privado");
        ChatService.Result result=service(t,r->{sent.set(r.deepCopy());return answer();}).chat(t.pair(CODE).token(),req);
        assertEquals(200,result.status()); assertEquals(0,sent.get().path("history").size());
        assertEquals("request-1",result.body().path("requestId").asText());
    }
    @Test void consentedHistoryRetainsOnlyValidatedBoundedMessages() throws Exception {
        SessionTokenStore t=tokens(); AtomicReference<JsonNode> sent=new AtomicReference<>();ObjectNode req=(ObjectNode)request();
        req.put("historyConsent",true);req.withArray("history").addObject().put("role","user").put("text","anterior");
        assertEquals(200,service(t,r->{sent.set(r);return answer();}).chat(t.pair(CODE).token(),req).status());
        assertEquals("anterior",sent.get().path("history").get(0).path("text").asText());
    }
    @Test void rateLimitRejectsEleventhRequest() throws Exception {
        SessionTokenStore t=tokens();String token=t.pair(CODE).token();ChatService s=service(t,r->answer());
        for(int i=0;i<10;i++)assertEquals(200,s.chat(token,request()).status());
        assertEquals(429,s.chat(token,request()).status());
    }
    @Test void timeoutIsRecoverableAndDoesNotExposeProviderException() throws Exception {
        SessionTokenStore t=tokens();ChatService.Result r=service(t,x->{throw new HttpTimeoutException("key-secret");}).chat(t.pair(CODE).token(),request());
        assertEquals(504,r.status()); assertTrue(r.body().path("recoverable").asBoolean()); assertFalse(r.body().toString().contains("secret"));
    }
    @Test void disabledEvenWithExistingCredentials() throws Exception {
        SessionTokenStore t=tokens();Provider p=OpenAiGateway.fromEnvironment(Map.of("OPENAI_API_KEY","not-a-real-key","OPENAI_MODEL","configured-model"));
        assertEquals(503,service(t,p).chat(t.pair(CODE).token(),request()).status());
    }
    @Test void inventedIdsEquipmentCoverageAndPrescriptionInvalidateProposal() throws Exception {
        ProposalValidator v=new ProposalValidator();
        for(String id:List.of("inventado","dumbbell-squat","sport-complementary-squat")) {
            ObjectNode out=(ObjectNode)answer();out.set("proposal",JSON.readTree("{\"title\":\"Ejemplo\",\"items\":[{\"exerciseId\":\""+id+"\",\"estimatedMinutes\":5,\"prescription\":[{\"key\":\"sets\",\"value\":\"2\"}],\"justification\":\"Ejemplo\"}]}"));
            assertThrows(IllegalArgumentException.class,()->v.validateResponse(out,request()));
        }
        ObjectNode out=(ObjectNode)answer();out.set("proposal",JSON.readTree("""
          {"title":"Ejemplo","items":[{"exerciseId":"bodyweight-squat","estimatedMinutes":5,
          "prescription":[{"key":"sets","value":"0"},{"key":"repetitions","value":"8"},{"key":"loadKg","value":"0"},{"key":"restSeconds","value":"60"}],"justification":"Ejemplo"}]}
          """));assertThrows(IllegalArgumentException.class,()->v.validateResponse(out,request()));
    }
    @Test void validProposalAndOnlyCorrelatedOpenCommandAllowed() throws Exception {
        ObjectNode out=(ObjectNode)answer();out.set("proposal",JSON.readTree("""
          {"title":"Ejemplo","items":[{"exerciseId":"bodyweight-squat","estimatedMinutes":5,
          "prescription":[{"key":"sets","value":"2"},{"key":"repetitions","value":"8"},{"key":"loadKg","value":"0"},{"key":"restSeconds","value":"60"}],"justification":"Ejemplo"}]}
          """)); out.withArray("commands").addObject().put("type","OPEN_EXERCISE").put("exerciseId","bodyweight-squat");
        new ProposalValidator().validateResponse(out,request());
        ((ObjectNode)out.path("commands").get(0)).put("exerciseId","chair-squat");
        assertThrows(IllegalArgumentException.class,()->new ProposalValidator().validateResponse(out,request()));
    }
    @Test void bodyweightExerciseCannotPrescribeUncataloguedExternalLoad() throws Exception {
        ObjectNode out=(ObjectNode)answer();out.set("proposal",JSON.readTree("""
          {"title":"Ejemplo","items":[{"exerciseId":"bodyweight-squat","estimatedMinutes":5,
          "prescription":[{"key":"sets","value":"2"},{"key":"repetitions","value":"8"},{"key":"loadKg","value":"10"},{"key":"restSeconds","value":"60"}],"justification":"Ejemplo"}]}
          """));assertThrows(IllegalArgumentException.class,()->new ProposalValidator().validateResponse(out,request()));
    }
    @Test void declaredMinuteBudgetCannotHideExcessivePrescriptionRest() throws Exception {
        ObjectNode out=(ObjectNode)answer();out.set("proposal",JSON.readTree("""
          {"title":"Ejemplo","items":[{"exerciseId":"bodyweight-squat","estimatedMinutes":5,
          "prescription":[{"key":"sets","value":"2"},{"key":"repetitions","value":"8"},{"key":"loadKg","value":"0"},{"key":"restSeconds","value":"600"}],"justification":"Ejemplo"}]}
          """));assertThrows(IllegalArgumentException.class,()->new ProposalValidator().validateResponse(out,request()));
    }
    @Test void strictJsonAndTransportRejectAmbiguousOrUnsafeInput() throws Exception {
        assertThrows(Exception.class,()->Json.parse("{\"message\":\"a\",\"message\":\"b\"}".getBytes()));
        assertThrows(Exception.class,()->Json.parse("{} {}".getBytes()));
        SessionTokenStore t=tokens();ChatService s=service(t,r->answer());
        assertThrows(IllegalArgumentException.class,()->Server.local(new InetSocketAddress("0.0.0.0",0),s,t));
        assertThrows(IllegalArgumentException.class,()->Server.configured(Map.of("ENERGYM_DEV_MODE","false"),s,t));
    }
    @Test void responsesEnvelopeRefusalIncompleteAndDuplicateJsonRejected() throws Exception {
        assertThrows(IllegalArgumentException.class,()->OpenAiGateway.parseResponse(JSON.readTree("{\"status\":\"incomplete\",\"output\":[]}")));
        assertThrows(IllegalArgumentException.class,()->OpenAiGateway.parseResponse(JSON.readTree("""
          {"status":"completed","output":[{"id":"msg_fixture","type":"message","status":"completed","role":"assistant","content":[{"type":"refusal","refusal":"No"}]}]}
          """)));
        ObjectNode envelope=JSON.createObjectNode().put("status","completed");
        envelope.putArray("output").addObject().put("id","msg_fixture").put("type","message").put("status","completed").put("role","assistant").putArray("content").addObject().put("type","output_text").put("text",answer().toString());
        assertEquals("Ejemplo sin revisión profesional.",OpenAiGateway.parseResponse(envelope).path("text").asText());
    }
    static ObjectNode completedEnvelope() throws Exception {
        ObjectNode envelope=JSON.createObjectNode().put("status","completed");
        envelope.putArray("output").addObject().put("id","msg_fixture").put("type","message").put("status","completed").put("role","assistant")
                .putArray("content").addObject().put("type","output_text").put("text",answer().toString());
        return envelope;
    }
    @Test void responsesEnvelopeUnexpectedToolAndIncompleteMessageRejected() throws Exception {
        ObjectNode envelope=completedEnvelope();ObjectNode message=(ObjectNode)envelope.path("output").get(0);
        message.put("status","incomplete");envelope.withArray("output").addObject().put("type","function_call").put("name","unexpected").put("arguments","{}");
        assertThrows(IllegalArgumentException.class,()->OpenAiGateway.parseResponse(envelope));
    }
    @Test void responsesEnvelopeObjectContentRejected() throws Exception {
        ObjectNode envelope=completedEnvelope();ObjectNode message=(ObjectNode)envelope.path("output").get(0);
        ObjectNode content=JSON.createObjectNode();content.set("arbitrary",message.path("content").get(0));message.set("content",content);
        assertThrows(IllegalArgumentException.class,()->OpenAiGateway.parseResponse(envelope));
    }
    @Test void responsesEnvelopeUnknownToolAndNonObjectEntriesRejected() throws Exception {
        for(String type:List.of("function_call","web_search_call","unknown")){
            ObjectNode envelope=completedEnvelope();envelope.withArray("output").addObject().put("type",type);
            assertThrows(IllegalArgumentException.class,()->OpenAiGateway.parseResponse(envelope),type);
        }
        for(JsonNode entry:List.of(JSON.nullNode(),JSON.getNodeFactory().textNode("unexpected"),JSON.createArrayNode())){
            ObjectNode envelope=completedEnvelope();envelope.withArray("output").add(entry);
            assertThrows(IllegalArgumentException.class,()->OpenAiGateway.parseResponse(envelope));
        }
    }
    @Test void responsesEnvelopeMessageMustBeCompletedAssistant() throws Exception {
        for(String status:List.of("incomplete","in_progress","")){
            ObjectNode envelope=completedEnvelope();ObjectNode message=(ObjectNode)envelope.path("output").get(0);
            if(status.isEmpty())message.remove("status");else message.put("status",status);
            assertThrows(IllegalArgumentException.class,()->OpenAiGateway.parseResponse(envelope),status);
        }
        ObjectNode envelope=completedEnvelope();((ObjectNode)envelope.path("output").get(0)).put("role","user");
        assertThrows(IllegalArgumentException.class,()->OpenAiGateway.parseResponse(envelope));
        ObjectNode incompleteRoot=completedEnvelope();incompleteRoot.put("status","incomplete");
        assertThrows(IllegalArgumentException.class,()->OpenAiGateway.parseResponse(incompleteRoot));
    }
    @Test void responsesEnvelopeOnlyOneMessageAndOneTextPartAllowed() throws Exception {
        ObjectNode extraEmptyMessage=completedEnvelope();extraEmptyMessage.withArray("output").addObject().put("id","msg_second").put("type","message").put("status","completed").put("role","assistant").putArray("content");
        assertThrows(IllegalArgumentException.class,()->OpenAiGateway.parseResponse(extraEmptyMessage));
        ObjectNode extraText=completedEnvelope();((ObjectNode)extraText.path("output").get(0)).withArray("content").addObject().put("type","output_text").put("text",answer().toString());
        assertThrows(IllegalArgumentException.class,()->OpenAiGateway.parseResponse(extraText));
        ObjectNode noText=completedEnvelope();((ObjectNode)noText.path("output").get(0)).putArray("content");
        assertThrows(IllegalArgumentException.class,()->OpenAiGateway.parseResponse(noText));
    }
    @Test void responsesEnvelopeWellFormedReasoningMayAccompanyFinalMessage() throws Exception {
        ObjectNode envelope=completedEnvelope();ObjectNode reasoning=envelope.withArray("output").addObject().put("id","rs_fixture").put("type","reasoning");
        reasoning.putArray("summary").addObject().put("type","summary_text").put("text","Resumen auxiliar");
        assertEquals("Ejemplo sin revisión profesional.",OpenAiGateway.parseResponse(envelope).path("text").asText());
        reasoning.put("status","completed");reasoning.put("encrypted_content","opaque_fixture");
        reasoning.putArray("content").addObject().put("type","reasoning_text").put("text","Contenido auxiliar");
        assertEquals("Ejemplo sin revisión profesional.",OpenAiGateway.parseResponse(envelope).path("text").asText());
    }
    @Test void responsesEnvelopeMalformedOrIncompleteReasoningRejected() throws Exception {
        for(String fixture:List.of(
                "{\"type\":\"reasoning\",\"summary\":[]}",
                "{\"id\":\"rs_fixture\",\"type\":\"reasoning\",\"summary\":{}}",
                "{\"id\":\"rs_fixture\",\"type\":\"reasoning\",\"summary\":[{\"type\":\"output_text\",\"text\":\"untrusted\"}]}",
                "{\"id\":\"rs_fixture\",\"type\":\"reasoning\",\"summary\":[],\"status\":\"incomplete\"}",
                "{\"id\":\"rs_fixture\",\"type\":\"reasoning\",\"summary\":[],\"status\":\"in_progress\"}",
                "{\"id\":\"rs_fixture\",\"type\":\"reasoning\",\"summary\":[],\"content\":{}}",
                "{\"id\":\"rs_fixture\",\"type\":\"reasoning\",\"summary\":[],\"encrypted_content\":42}",
                "{\"id\":\"rs_fixture\",\"type\":\"reasoning\",\"summary\":[],\"unexpected\":true}")){
            ObjectNode envelope=completedEnvelope();envelope.withArray("output").add(JSON.readTree(fixture));
            assertThrows(IllegalArgumentException.class,()->OpenAiGateway.parseResponse(envelope),fixture);
        }
    }
    @Test void strictResponsesPayloadNeverRequestsStorageOrTools() throws Exception {
        OpenAiGateway gateway=(OpenAiGateway)OpenAiGateway.fromEnvironment(Map.of("OPENAI_ENABLED","true","OPENAI_API_KEY","not-real","OPENAI_MODEL","explicit-model"));
        JsonNode payload=gateway.payload(new ProposalValidator().providerRequest(request()));
        assertFalse(payload.path("store").asBoolean());assertTrue(payload.path("text").path("format").path("strict").asBoolean());
        assertEquals("json_schema",payload.path("text").path("format").path("type").asText());assertFalse(payload.has("tools"));
    }
    @Test void sportSpecificPendingCoverageNeverBecomesEligible() throws Exception {
        ObjectNode req=(ObjectNode)request();ObjectNode p=(ObjectNode)req.get("profile");
        p.put("sportId","football");p.put("category","SPORT");
        ProposalValidator validator=new ProposalValidator();validator.validateRequest(req);
        assertTrue(validator.providerRequest(req).path("eligibleExercises").isEmpty());
        p.put("focus","COMPLEMENTARY");
        assertFalse(validator.providerRequest(req).path("eligibleExercises").isEmpty());
    }
    @Test void invalidProviderResponseReturnsNoProposalOrCommands() throws Exception {
        SessionTokenStore t=tokens();ObjectNode response=(ObjectNode)answer();response.put("text","");
        ChatService.Result result=service(t,r->response).chat(t.pair(CODE).token(),request());
        assertEquals(502,result.status());assertEquals("INVALID_PROVIDER_RESPONSE",result.body().path("error").asText());
        assertFalse(result.body().has("proposal"));assertFalse(result.body().has("commands"));
    }
    @Test void realLocalHttpSmokeChecksAuthPairingDisabledProviderAndBoundedBody() throws Exception {
        SessionTokenStore t=tokens();
        try(Server server=Server.local(new InetSocketAddress("127.0.0.1",0),service(t,OpenAiGateway.fromEnvironment(Map.of())),t)){
            server.start();HttpClient c=HttpClient.newHttpClient();String base="http://127.0.0.1:"+server.port();
            assertEquals(200,c.send(HttpRequest.newBuilder(URI.create(base+"/health")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(401,post(c,base+"/api/chat",request().toString(),null).statusCode());
            HttpResponse<String> paired=post(c,base+"/api/session","{\"pairingCode\":\""+CODE+"\"}",null);assertEquals(201,paired.statusCode());
            String token=JSON.readTree(paired.body()).path("token").asText();
            assertEquals(503,post(c,base+"/api/chat",request().toString(),token).statusCode());
            assertEquals(413,post(c,base+"/api/chat","x".repeat(32769),token).statusCode());
            assertEquals(401,post(c,base+"/api/session","{\"pairingCode\":\""+CODE+"\"}",null).statusCode());
        }
    }
    static HttpResponse<String> post(HttpClient c,String uri,String body,String token)throws Exception{
        HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(uri)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if(token!=null)b.header("Authorization","Bearer "+token);return c.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
}
