package io.grouploom.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;

class OpenApiConfigTest {

	private OpenAPI customized() {
		OpenAPI api = new OpenAPI()
			.components(new Components().addSchemas("PagedModelThing", new ObjectSchema()
				.addProperty("content", new Schema<>())
				.addProperty("page", new Schema<>())))
			.paths(new Paths().addPathItem("/things", new PathItem().get(new Operation().responses(new ApiResponses()))));
		new OpenApiConfig().problemDetailCustomizer().customise(api);
		return api;
	}

	@Test
	void customise_addsProblemDetailWithErrorsCodeAndField() {
		Schema<?> problem = customized().getComponents().getSchemas().get("ProblemDetail");

		assertThat(problem.getProperties()).containsKeys("status", "detail", "errors", "code", "field");
	}

	@Test
	void customise_addsDefaultProblemJsonResponseToEveryOperation() {
		OpenAPI api = customized();

		assertThat(api.getPaths().get("/things").getGet().getResponses().get("default")
			.getContent().get("application/problem+json")).isNotNull();
	}

	@Test
	void customise_marksPagedModelContentAndPageAsRequired() {
		Schema<?> paged = customized().getComponents().getSchemas().get("PagedModelThing");

		assertThat(paged.getRequired()).containsExactlyInAnyOrder("content", "page");
	}

}
