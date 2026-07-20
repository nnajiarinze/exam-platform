package se.medbo.examplatform.content.review;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.medbo.examplatform.content.shared.DomainException;

@Service
public class ReviewWorkflowService {
    private static final Set<String> REASONS=Set.of("FACTUALLY_INCORRECT","INSUFFICIENT_SOURCE_SUPPORT","OUTDATED_SOURCE","AMBIGUOUS_WORDING","POOR_DISTRACTOR_QUALITY","MULTIPLE_PLAUSIBLE_ANSWERS","INCORRECT_CORRECT_ANSWER","INVALID_DIFFICULTY","DUPLICATE_CONTENT","OUT_OF_SCOPE","LEGAL_OR_POLICY_CONCERN","FORMATTING_OR_LANGUAGE","OTHER");
    private final JdbcClient jdbc;
    public ReviewWorkflowService(JdbcClient jdbc){this.jdbc=jdbc;}

    @Transactional public void submitted(String type,UUID contentId,UUID contentVersionId,String author,String lifecycle,boolean resubmission){
        var now=now();boolean isResubmission=resubmission||jdbc.sql("SELECT EXISTS(SELECT 1 FROM review_item WHERE content_type=:type AND content_id=:content)").param("type",type).param("content",contentId).query(Boolean.class).single();var existing=jdbc.sql("SELECT id FROM review_item WHERE content_type=:type AND content_version_id=:version").param("type",type).param("version",contentVersionId).query(UUID.class).optional();UUID id;
        if(existing.isPresent()){id=existing.get();jdbc.sql("UPDATE review_item SET review_status='UNDER_REVIEW',lifecycle_status=:lifecycle,submitted_at=:now,updated_at=:now,assigned_reviewer_id=NULL,assigned_at=NULL,version=version+1 WHERE id=:id").param("lifecycle",lifecycle).param("now",now).param("id",id).update();}
        else{id=UUID.randomUUID();jdbc.sql("INSERT INTO review_item(id,content_type,content_id,content_version_id,author_id,review_status,lifecycle_status,submitted_at,updated_at) VALUES(:id,:type,:content,:version,:author,'UNDER_REVIEW',:lifecycle,:now,:now)").param("id",id).param("type",type).param("content",contentId).param("version",contentVersionId).param("author",author).param("lifecycle",lifecycle).param("now",now).update();}
        record(id,contentVersionId,isResubmission?"RESUBMITTED":"SUBMITTED",isResubmission?"REQUIRES_UPDATE":"UNREVIEWED","UNDER_REVIEW",null,null,now);
    }

    @Transactional public void decision(String type,UUID contentVersionId,String from,String to,String lifecycle,String comment,String reasonCode){
        validateFeedback(to,reasonCode,comment);var item=findByVersion(type,contentVersionId);var now=now();jdbc.sql("UPDATE review_item SET review_status=:status,lifecycle_status=:lifecycle,updated_at=:now,version=version+1 WHERE id=:id").param("status",to).param("lifecycle",lifecycle).param("now",now).param("id",item.id()).update();record(item.id(),contentVersionId,to,from,to,comment,reasonCode,now);
    }

    public void validateFeedback(String action,String reasonCode,String comment){if(reasonCode!=null&&!REASONS.contains(reasonCode))throw validation("Unsupported review reason code");if(List.of("REJECTED","REQUIRES_UPDATE").contains(action)){if((reasonCode==null||reasonCode.isBlank())&&(comment==null||comment.trim().length()<10))throw validation("A reason code or descriptive comment is required");if("OTHER".equals(reasonCode)&&(comment==null||comment.isBlank()))throw validation("A comment is required for OTHER");}}

    private Item findByVersion(String type,UUID version){return jdbc.sql("SELECT id,version FROM review_item WHERE content_type=:type AND content_version_id=:contentVersion").param("type",type).param("contentVersion",version).query((rs,n)->new Item(rs.getObject("id",UUID.class),rs.getLong("version"))).optional().orElseThrow(()->new DomainException(HttpStatus.NOT_FOUND,"REVIEW_ITEM_NOT_FOUND","Review item was not found"));}
    private void record(UUID item,UUID contentVersion,String action,String from,String to,String comment,String reason,OffsetDateTime now){jdbc.sql("INSERT INTO review_record(id,review_item_id,content_version_id,action,from_status,to_status,actor_id,actor_roles,comment,reason_code,created_at) VALUES(:id,:item,:version,:action,:from,:to,:actor,:roles,:comment,:reason,:now)").param("id",UUID.randomUUID()).param("item",item).param("version",contentVersion).param("action",action).param("from",from,Types.VARCHAR).param("to",to,Types.VARCHAR).param("actor",actor()).param("roles",roles()).param("comment",blank(comment),Types.VARCHAR).param("reason",blank(reason),Types.VARCHAR).param("now",now).update();}
    String actor(){return SecurityContextHolder.getContext().getAuthentication().getName();}
    private String roles(){return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().map(Object::toString).sorted().reduce((a,b)->a+","+b).orElse("");}
    private String blank(String v){return v==null||v.isBlank()?null:v.trim();}
    private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
    private DomainException validation(String message){return new DomainException(HttpStatus.UNPROCESSABLE_ENTITY,"REVIEW_REASON_REQUIRED",message);}
    record Item(UUID id,long version){}
}
