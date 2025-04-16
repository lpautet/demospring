package net.pautet.softs.demospring.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class NetatmoErrorResponse {
    @JsonProperty("error")
    private Error error;

    @JsonCreator
    public NetatmoErrorResponse(@JsonProperty("error") Error error) {
        this.error = error;
    }

    public NetatmoErrorResponse(int code, String message) {
        this.error = new Error(code, message);
    }

    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    @Data
    public static class Error {
        @JsonProperty("code")
        private int code;
        
        @JsonProperty("message")
        private String message;

        @JsonCreator
        public Error(@JsonProperty("code") int code, @JsonProperty("message") String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
} 