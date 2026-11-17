package com.thinklab.application.dto.request;

import com.thinklab.domain.model.InstallKit.Family;
import com.thinklab.domain.model.InstallKit.Language;
import com.thinklab.domain.model.InstallKit.Role;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTO for {@code update}: the whole definition again. */
@Serdeable
public record UpdateInstallKitRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must not exceed 80 characters")
        String name,
        @NotNull(message = "The family is required") Family family,
        @NotNull(message = "The role is required") Role role,
        @NotNull(message = "The language is required") Language language,
        @NotBlank(message = "The system id is required") @Size(max = 40, message = "The system id must not exceed 40 characters") String systemId,
        @NotBlank(message = "The machine class is required") @Size(max = 40, message = "The machine class must not exceed 40 characters") String machineClass
) {}
