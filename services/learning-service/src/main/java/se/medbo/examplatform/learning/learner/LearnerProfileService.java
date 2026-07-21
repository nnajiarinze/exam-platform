package se.medbo.examplatform.learning.learner;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearnerProfileService {
    private final JdbcClient jdbc;
    public LearnerProfileService(JdbcClient jdbc){this.jdbc=jdbc;}
    public Profile get(UUID id){return jdbc.sql("SELECT id,email,email_verified,display_name,interface_language,explanation_language,account_status,onboarding_completed,created_at,updated_at,deleted_at FROM learner_profile WHERE id=:id").param("id",id).query((rs,n)->new Profile(rs.getObject("id",UUID.class),rs.getString("email"),rs.getBoolean("email_verified"),rs.getString("display_name"),rs.getString("interface_language"),rs.getString("explanation_language"),rs.getString("account_status"),rs.getBoolean("onboarding_completed"),rs.getObject("created_at",OffsetDateTime.class),rs.getObject("updated_at",OffsetDateTime.class),rs.getObject("deleted_at",OffsetDateTime.class))).single();}
    @Transactional public Profile update(UUID id,String displayName,String interfaceLanguage,String explanationLanguage,boolean onboardingCompleted){jdbc.sql("UPDATE learner_profile SET display_name=:name,interface_language=:interface,explanation_language=:explanation,onboarding_completed=:onboarding,updated_at=now() WHERE id=:id AND account_status='ACTIVE'").param("name",displayName).param("interface",interfaceLanguage).param("explanation",explanationLanguage).param("onboarding",onboardingCompleted).param("id",id).update();return get(id);}
    @Transactional public void delete(UUID id){jdbc.sql("UPDATE learner_profile SET email=NULL,display_name='Deleted learner',email_verified=false,account_status='DELETED',onboarding_completed=false,deleted_at=now(),updated_at=now() WHERE id=:id AND account_status='ACTIVE'").param("id",id).update();}
    public record Profile(UUID id,String email,boolean emailVerified,String displayName,String interfaceLanguage,String explanationLanguage,String accountStatus,boolean onboardingCompleted,OffsetDateTime createdAt,OffsetDateTime updatedAt,OffsetDateTime deletedAt){}
}
