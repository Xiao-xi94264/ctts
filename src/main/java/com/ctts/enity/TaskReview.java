package com.ctts.enity;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Long reviewerId;
    private Integer score;
    private String comment;
    private LocalDateTime reviewTime;
}
