package com.ptt.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactListDTO {
    private String contactID;
    private String fullName;
    private String surName;
    private String email;
    private String phoneNum;
    private String status;
}
