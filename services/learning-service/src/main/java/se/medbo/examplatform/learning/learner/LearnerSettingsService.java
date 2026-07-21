package se.medbo.examplatform.learning.learner;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.medbo.examplatform.learning.shared.ApiException;

@Service
public class LearnerSettingsService {
    private final JdbcClient jdbc;
    public LearnerSettingsService(JdbcClient jdbc){this.jdbc=jdbc;}

    @Transactional
    public Settings get(UUID learnerId){
        jdbc.sql("INSERT INTO learner_settings(learner_id) VALUES(:id) ON CONFLICT(learner_id) DO NOTHING").param("id",learnerId).update();
        return load(learnerId);
    }

    @Transactional
    public Settings update(UUID learnerId, Update command){
        validateTimezone(command.timezone());
        get(learnerId);
        int updated=jdbc.sql("""
            UPDATE learner_settings SET daily_question_goal=:daily,weekly_study_days_goal=:weekly,
              study_reminder_enabled=:reminder,preferred_reminder_time=:time,timezone=:timezone,
              progress_summary_enabled=:summary,achievement_notifications_enabled=:achievements,
              version=version+1,updated_at=now()
            WHERE learner_id=:id AND version=:version
            """).param("daily",command.dailyQuestionGoal()).param("weekly",command.weeklyStudyDaysGoal())
            .param("reminder",command.studyReminderEnabled()).param("time",command.preferredReminderTime())
            .param("timezone",command.timezone()).param("summary",command.progressSummaryEnabled())
            .param("achievements",command.achievementNotificationsEnabled()).param("id",learnerId)
            .param("version",command.version()).update();
        if(updated==0)throw new ApiException(HttpStatus.CONFLICT,"LEARNER_SETTINGS_CONFLICT","Settings were changed on another device");
        return load(learnerId);
    }

    private Settings load(UUID learnerId){
        var row=jdbc.sql("SELECT * FROM learner_settings WHERE learner_id=:id").param("id",learnerId).query((rs,n)->new Row(
            rs.getInt("daily_question_goal"),rs.getInt("weekly_study_days_goal"),rs.getBoolean("study_reminder_enabled"),
            rs.getObject("preferred_reminder_time",LocalTime.class),rs.getString("timezone"),rs.getBoolean("progress_summary_enabled"),
            rs.getBoolean("achievement_notifications_enabled"),rs.getLong("version"),rs.getObject("created_at",OffsetDateTime.class),rs.getObject("updated_at",OffsetDateTime.class))).single();
        var zone=validateTimezone(row.timezone());
        var today=LocalDate.now(zone);var weekStart=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int todayAnswers=jdbc.sql("SELECT COUNT(*) FROM practice_response pr JOIN practice_session_question psq ON psq.id=pr.practice_session_question_id JOIN practice_session ps ON ps.id=psq.practice_session_id WHERE ps.learner_id=:id AND (pr.answered_at AT TIME ZONE :timezone)::date=:today")
            .param("id",learnerId).param("timezone",row.timezone()).param("today",today).query(Integer.class).single();
        int studyDays=jdbc.sql("SELECT COUNT(DISTINCT (pr.answered_at AT TIME ZONE :timezone)::date) FROM practice_response pr JOIN practice_session_question psq ON psq.id=pr.practice_session_question_id JOIN practice_session ps ON ps.id=psq.practice_session_id WHERE ps.learner_id=:id AND (pr.answered_at AT TIME ZONE :timezone)::date BETWEEN :start AND :end")
            .param("id",learnerId).param("timezone",row.timezone()).param("start",weekStart).param("end",today).query(Integer.class).single();
        return new Settings(row.dailyQuestionGoal(),row.weeklyStudyDaysGoal(),row.studyReminderEnabled(),row.preferredReminderTime(),row.timezone(),row.progressSummaryEnabled(),row.achievementNotificationsEnabled(),row.version(),todayAnswers,studyDays,row.createdAt(),row.updatedAt());
    }

    private static ZoneId validateTimezone(String timezone){try{return ZoneId.of(timezone);}catch(Exception ignored){throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_TIMEZONE","Timezone must be a valid IANA timezone");}}
    private record Row(int dailyQuestionGoal,int weeklyStudyDaysGoal,boolean studyReminderEnabled,LocalTime preferredReminderTime,String timezone,boolean progressSummaryEnabled,boolean achievementNotificationsEnabled,long version,OffsetDateTime createdAt,OffsetDateTime updatedAt){}
    public record Update(int dailyQuestionGoal,int weeklyStudyDaysGoal,boolean studyReminderEnabled,LocalTime preferredReminderTime,String timezone,boolean progressSummaryEnabled,boolean achievementNotificationsEnabled,long version){}
    public record Settings(int dailyQuestionGoal,int weeklyStudyDaysGoal,boolean studyReminderEnabled,LocalTime preferredReminderTime,String timezone,boolean progressSummaryEnabled,boolean achievementNotificationsEnabled,long version,int questionsAnsweredToday,int studyDaysThisWeek,OffsetDateTime createdAt,OffsetDateTime updatedAt){}
}
