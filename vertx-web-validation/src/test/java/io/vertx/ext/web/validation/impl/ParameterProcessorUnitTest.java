package io.vertx.ext.web.validation.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.json.schema.SchemaParser;
import io.vertx.ext.json.schema.SchemaRouter;
import io.vertx.ext.json.schema.SchemaRouterOptions;
import io.vertx.ext.json.schema.ValidationException;
import io.vertx.ext.json.schema.draft7.Draft7SchemaParser;
import io.vertx.ext.web.validation.MalformedValueException;
import io.vertx.ext.web.validation.ParameterProcessorException;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.impl.parameter.ParameterParser;
import io.vertx.ext.web.validation.impl.parameter.ParameterProcessor;
import io.vertx.ext.web.validation.impl.parameter.ParameterProcessorImpl;
import io.vertx.ext.web.validation.impl.validator.ValueValidator;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
@ExtendWith(MockitoExtension.class)
public class ParameterProcessorUnitTest {

  SchemaRouter router;
  SchemaParser parser;

  @Mock
  ParameterParser mockedParser;
  @Mock
  ValueValidator mockedValidator;

  @BeforeEach
  public void setUp(Vertx vertx) {
    router = SchemaRouter.create(vertx, new SchemaRouterOptions());
    parser = Draft7SchemaParser.create(router);
  }

  @Test
  public void testRequiredParam() {
    ParameterProcessor processor = new ParameterProcessorImpl(
      "myParam",
      ParameterLocation.QUERY,
      false,
      mockedParser,
      mockedValidator
    );

    when(mockedParser.parseParameter(any())).thenReturn(null);
    assertThatCode(() -> processor.process(new HashMap<>()))
      .isInstanceOf(ParameterProcessorException.class)
      .hasFieldOrPropertyWithValue("errorType", ParameterProcessorException.ParameterProcessorErrorType.MISSING_PARAMETER_WHEN_REQUIRED_ERROR)
      .hasFieldOrPropertyWithValue("location", ParameterLocation.QUERY)
      .hasFieldOrPropertyWithValue("parameterName", "myParam")
      .hasNoCause();
  }

  @Test
  public void testOptionalParam(VertxTestContext testContext) {
    ParameterProcessor processor = new ParameterProcessorImpl(
      "myParam",
      ParameterLocation.QUERY,
      true,
      mockedParser,
      mockedValidator
    );

    when(mockedParser.parseParameter(any())).thenReturn(null);

    processor.process(new HashMap<>()).setHandler(testContext.succeeding(value -> {
      testContext.verify(() ->
        assertThat(value).isNull()
      );
      testContext.completeNow();
    }));
  }


  @Test
  public void testOptionalParamWithDefault(VertxTestContext testContext) {
    ParameterProcessor processor = new ParameterProcessorImpl(
      "myParam",
      ParameterLocation.QUERY,
      true,
      mockedParser,
      mockedValidator
    );

    when(mockedParser.parseParameter(any())).thenReturn(null);
    when(mockedValidator.getDefault()).thenReturn("bla");

    processor.process(new HashMap<>()).setHandler(testContext.succeeding(value -> {
      testContext.verify(() ->
        assertThat(value.getString()).isEqualTo("bla")
      );
      testContext.completeNow();
    }));
  }

  @Test
  public void testParsingFailure() {
    ParameterProcessor processor = new ParameterProcessorImpl(
      "myParam",
      ParameterLocation.QUERY,
      false,
      mockedParser,
      mockedValidator
    );

    when(mockedParser.parseParameter(any())).thenThrow(new MalformedValueException("bla"));

    assertThatCode(() -> processor.process(new HashMap<>()))
      .isInstanceOf(ParameterProcessorException.class)
      .hasFieldOrPropertyWithValue("errorType", ParameterProcessorException.ParameterProcessorErrorType.PARSING_ERROR)
      .hasFieldOrPropertyWithValue("location", ParameterLocation.QUERY)
      .hasFieldOrPropertyWithValue("parameterName", "myParam")
      .hasCauseInstanceOf(MalformedValueException.class);
  }

  @Test
  public void testValidation(VertxTestContext testContext) {
    ParameterProcessor processor = new ParameterProcessorImpl(
      "myParam",
      ParameterLocation.QUERY,
      true,
      mockedParser,
      mockedValidator
    );

    when(mockedParser.parseParameter(any())).thenReturn("aaa");
    when(mockedValidator.validate(any())).thenReturn(Future.succeededFuture(RequestParameter.create("aaa")));

    processor.process(new HashMap<>()).setHandler(testContext.succeeding(rp -> {
      testContext.verify(() -> {
        assertThat(rp.isString()).isTrue();
        assertThat(rp.getString()).isEqualTo("aaa");
      });
      testContext.completeNow();
    }));
  }

  @Test
  public void testValidationFailure(VertxTestContext testContext) {
    ParameterProcessor processor = new ParameterProcessorImpl(
      "myParam",
      ParameterLocation.QUERY,
      true,
      mockedParser,
      mockedValidator
    );

    when(mockedParser.parseParameter(any())).thenReturn("aaa");
    when(mockedValidator.validate(any())).thenReturn(Future.failedFuture(ValidationException.createException("aaa", "aaa", "aaa")));

    processor.process(new HashMap<>()).setHandler(testContext.failing(throwable -> {
      testContext.verify(() -> {
        assertThat(throwable)
          .isInstanceOf(ParameterProcessorException.class)
          .hasFieldOrPropertyWithValue("errorType", ParameterProcessorException.ParameterProcessorErrorType.VALIDATION_ERROR)
          .hasFieldOrPropertyWithValue("location", ParameterLocation.QUERY)
          .hasFieldOrPropertyWithValue("parameterName", "myParam")
          .hasCauseInstanceOf(ValidationException.class);
      });
      testContext.completeNow();
    }));
  }
}
