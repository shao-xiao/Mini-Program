package com.dehui.property.modules.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileMeResponse {
    private boolean loginStatus;
    private String role;
    private boolean tenantBound;
    private boolean staffBound;
    private BoundInfo tenantInfo;
    private BoundInfo staffInfo;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoundInfo {
        private Long id;
        private String name;
        private String username;
    }
}
