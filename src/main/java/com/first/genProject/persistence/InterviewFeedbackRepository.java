package com.first.genProject.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.first.genProject.dto.TopicProgress;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedbackEntity, UUID> {
    Page<InterviewFeedbackEntity> findByAnswer_Question_Id(UUID questionId, Pageable pageable);

    @Query("""
            select new com.first.genProject.dto.TopicProgress(
                s.track, s.topic, s.difficulty, count(f), count(distinct q.id),
                avg(f.score), max(f.score), max(a.submittedAt))
            from InterviewFeedbackEntity f join f.answer a join a.question q join q.session s
            group by s.track, s.topic, s.difficulty
            order by s.track, s.topic, s.difficulty
            """)
    List<TopicProgress> topicProgress();
}
