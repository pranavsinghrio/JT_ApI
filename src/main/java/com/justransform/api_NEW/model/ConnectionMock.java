package com.justransform.api_NEW.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.justransform.common.vo.ConnectionVo;

public class ConnectionMock {

    @JsonProperty("Connections")
    private ConnectionVo connectionVo;

    public ConnectionVo getConnectionVo() {
        return connectionVo;
    }

    public void setConnectionVo(ConnectionVo connectionVo) {
        this.connectionVo = connectionVo;
    }
}
