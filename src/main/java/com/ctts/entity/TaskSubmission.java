package com.ctts.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskSubmission {
    private Long id;
    private Long taskId;
    private Long userId;
    private String content;
    private String fileUrl;
    private LocalDateTime submitTime;
    private Integer status;
}
