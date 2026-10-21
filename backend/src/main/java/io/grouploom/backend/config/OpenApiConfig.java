package io.grouploom.backend.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;
import org.springdoc.core.customizers.OpenApiCustomizer;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;

@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

	// Documents RFC 9457 errors so the generated frontend types include ProblemDetail.
	@Bean
	OpenApiCustomizer problemDetailCustomizer() {
		return openApi -> {
			Map<String, Schema> resolved = ModelConverters.getInstance().read(ProblemDetail.class);
			resolved.forEach(openApi.getComponents()::addSchemas);
			Schema<?> problem = openApi.getComponents().getSchemas().get("ProblemDetail");
			problem.addProperty("errors", new ArraySchema().items(new ObjectSchema()
				.addProperty("field", new StringSchema())
				.addProperty("message", new StringSchema())));
			ApiResponse error = new ApiResponse().description("Error").content(new Content()
				.addMediaType("application/problem+json",
					new io.swagger.v3.oas.models.media.MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))));
			openApi.getPaths().values().forEach(path -> path.readOperations()
				.forEach(op -> op.getResponses().addApiResponse("default", error)));
		};
	}

}
