package com.ctts.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskReview {
    private Long id;
    private Long submissionId;
    private Long reviewerId;
    private Integer score;
    private String comment;
    private LocalDateTime reviewTime;
}
