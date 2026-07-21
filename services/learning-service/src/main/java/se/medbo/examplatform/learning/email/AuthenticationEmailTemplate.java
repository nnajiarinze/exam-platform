package se.medbo.examplatform.learning.email;

final class AuthenticationEmailTemplate {
    private AuthenticationEmailTemplate() {}
    static TransactionalEmail verification(String recipient,String displayName,String verificationUrl){
        String name=escape(displayName),url=escape(verificationUrl);
        return new TransactionalEmail(recipient,"Verify your Svea Study account","Hello "+displayName+",\n\nVerify your account: "+verificationUrl,
                "<p>Hello "+name+",</p><p><a href=\""+url+"\">Verify your account</a></p>","account-verification");
    }
    static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
