package com.flowengine.dto;

import com.flowengine.domain.enums.DbOperationType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class DbNodeDTO extends BaseNodeDTO {
    private String jdbcUrl;
    private String username;
    private String password;
    private DbOperationType operationType;
    private String sql;
    private Map<String, Object> parameters;
    private Boolean asyncSave;
}
