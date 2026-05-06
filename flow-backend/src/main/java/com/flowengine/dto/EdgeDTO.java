package com.flowengine.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EdgeDTO {
    private String id;
    private String source;
    private String target;
    private String condition;
}
