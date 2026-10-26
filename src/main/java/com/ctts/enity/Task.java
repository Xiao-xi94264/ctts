package com.ctts.enity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Task {
    private Long id;
    private Long projectId;
    private String title;
    private String description;
    private LocalDateTime deadline;
    private Integer totalScore;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
