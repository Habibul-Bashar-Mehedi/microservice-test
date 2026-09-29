package com.example.logservice.repository;

import com.example.logservice.entity.MessageLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {

    List<MessageLog> findAllByOrderByCreatedAtDescIdDesc();
}