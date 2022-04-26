/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.service.invoker;


import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_CBOR_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;


/**
 * Tests for {@link HttpServiceMethod} with a test {@link TestHttpClientAdapter}
 * that stubs the client invocations.
 *
 * <p>The tests do not create nor invoke {@code HttpServiceMethod} directly but
 * rather use {@link HttpServiceProxyFactory} to create a service proxy in order to
 * use a strongly typed interface without the need for class casts.
 *
 * @author Rossen Stoyanchev
 */
public class HttpServiceMethodTests {

	private static final ParameterizedTypeReference<String> BODY_TYPE = new ParameterizedTypeReference<>() {};


	private final TestHttpClientAdapter clientAdapter = new TestHttpClientAdapter();


	@Test
	void reactorService() {
		ReactorService service = this.clientAdapter.createService(ReactorService.class);

		Mono<Void> voidMono = service.execute();
		StepVerifier.create(voidMono).verifyComplete();
		verifyClientInvocation("requestToVoid", null);

		Mono<HttpHeaders> headersMono = service.getHeaders();
		StepVerifier.create(headersMono).expectNextCount(1).verifyComplete();
		verifyClientInvocation("requestToHeaders", null);

		Mono<String> body = service.getBody();
		StepVerifier.create(body).expectNext("requestToBody").verifyComplete();
		verifyClientInvocation("requestToBody", BODY_TYPE);

		Flux<String> fluxBody = service.getFluxBody();
		StepVerifier.create(fluxBody).expectNext("request", "To", "Body", "Flux").verifyComplete();
		verifyClientInvocation("requestToBodyFlux", BODY_TYPE);

		Mono<ResponseEntity<Void>> voidEntity = service.getVoidEntity();
		StepVerifier.create(voidEntity).expectNext(ResponseEntity.ok().build()).verifyComplete();
		verifyClientInvocation("requestToBodilessEntity", null);

		Mono<ResponseEntity<String>> entity = service.getEntity();
		StepVerifier.create(entity).expectNext(ResponseEntity.ok("requestToEntity"));
		verifyClientInvocation("requestToEntity", BODY_TYPE);

		Mono<ResponseEntity<Flux<String>>> fluxEntity= service.getFluxEntity();
		StepVerifier.create(fluxEntity.flatMapMany(HttpEntity::getBody)).expectNext("request", "To", "Entity", "Flux").verifyComplete();
		verifyClientInvocation("requestToEntityFlux", BODY_TYPE);
	}

	@Test
	void rxJavaService() {
		RxJavaService service = this.clientAdapter.createService(RxJavaService.class);
		Completable completable = service.execute();
		assertThat(completable).isNotNull();

		Single<HttpHeaders> headersSingle = service.getHeaders();
		assertThat(headersSingle.blockingGet()).isNotNull();

		Single<String> bodySingle = service.getBody();
		assertThat(bodySingle.blockingGet()).isEqualTo("requestToBody");

		Flowable<String> bodyFlow = service.getFlowableBody();
		assertThat(bodyFlow.toList().blockingGet()).asList().containsExactly("request", "To", "Body", "Flux");

		Single<ResponseEntity<Void>> voidEntity = service.getVoidEntity();
		assertThat(voidEntity.blockingGet().getBody()).isNull();

		Single<ResponseEntity<String>> entitySingle = service.getEntity();
		assertThat(entitySingle.blockingGet().getBody()).isEqualTo("requestToEntity");

		Single<ResponseEntity<Flowable<String>>> entityFlow = service.getFlowableEntity();
		Flowable<String> body = (entityFlow.blockingGet()).getBody();
		assertThat(body.toList().blockingGet()).containsExactly("request", "To", "Entity", "Flux");
	}

	@Test
	void blockingService() {

		BlockingService service = this.clientAdapter.createService(BlockingService.class);

		service.execute();

		HttpHeaders headers = service.getHeaders();
		assertThat(headers).isNotNull();

		String body = service.getBody();
		assertThat(body).isEqualTo("requestToBody");

		ResponseEntity<String> entity = service.getEntity();
		assertThat(entity.getBody()).isEqualTo("requestToEntity");

		ResponseEntity<Void> voidEntity = service.getVoidEntity();
		assertThat(voidEntity.getBody()).isNull();
	}

	@Test
	void methodAnnotatedService() {

		MethodAnnotatedService service = this.clientAdapter.createService(MethodAnnotatedService.class);

		service.performGet();

		HttpRequestSpec request = this.clientAdapter.getRequestSpec();
		assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.GET);
		assertThat(request.getUriTemplate()).isNull();
		assertThat(request.getHeaders().getContentType()).isNull();
		assertThat(request.getHeaders().getAccept()).isEmpty();

		service.performPost();

		request = this.clientAdapter.getRequestSpec();
		assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.POST);
		assertThat(request.getUriTemplate()).isEqualTo("/url");
		assertThat(request.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
		assertThat(request.getHeaders().getAccept()).containsExactly(MediaType.APPLICATION_JSON);
	}

	@Test
	void typeAndMethodAnnotatedService() {

		MethodAnnotatedService service = this.clientAdapter.createService(TypeAndMethodAnnotatedService.class);

		service.performGet();

		HttpRequestSpec request = this.clientAdapter.getRequestSpec();
		assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.GET);
		assertThat(request.getUriTemplate()).isEqualTo("/base");
		assertThat(request.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_CBOR);
		assertThat(request.getHeaders().getAccept()).containsExactly(MediaType.APPLICATION_CBOR);

		service.performPost();

		request = this.clientAdapter.getRequestSpec();
		assertThat(request.getHttpMethod()).isEqualTo(HttpMethod.POST);
		assertThat(request.getUriTemplate()).isEqualTo("/base/url");
		assertThat(request.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
		assertThat(request.getHeaders().getAccept()).containsExactly(MediaType.APPLICATION_JSON);
	}

	private void verifyClientInvocation(String methodName, @Nullable ParameterizedTypeReference<?> expectedBodyType) {
		assertThat((this.clientAdapter.getInvokedMethodName())).isEqualTo(methodName);
		assertThat(this.clientAdapter.getBodyType()).isEqualTo(expectedBodyType);
	}


	@SuppressWarnings("unused")
	private interface ReactorService {

		@HttpExchange
		Mono<Void> execute();

		@GetExchange
		Mono<HttpHeaders> getHeaders();

		@GetExchange
		Mono<String> getBody();

		@GetExchange
		Flux<String> getFluxBody();

		@GetExchange
		Mono<ResponseEntity<Void>> getVoidEntity();

		@GetExchange
		Mono<ResponseEntity<String>> getEntity();

		@GetExchange
		Mono<ResponseEntity<Flux<String>>> getFluxEntity();
	}


	@SuppressWarnings("unused")
	private interface RxJavaService {

		@HttpExchange
		Completable execute();

		@GetExchange
		Single<HttpHeaders> getHeaders();

		@GetExchange
		Single<String> getBody();

		@GetExchange
		Flowable<String> getFlowableBody();

		@GetExchange
		Single<ResponseEntity<Void>> getVoidEntity();

		@GetExchange
		Single<ResponseEntity<String>> getEntity();

		@GetExchange
		Single<ResponseEntity<Flowable<String>>> getFlowableEntity();
	}


	@SuppressWarnings("unused")
	private interface BlockingService {

		@HttpExchange
		void execute();

		@GetExchange
		HttpHeaders getHeaders();

		@GetExchange
		String getBody();

		@GetExchange
		ResponseEntity<Void> getVoidEntity();

		@GetExchange
		ResponseEntity<String> getEntity();
	}


	@SuppressWarnings("unused")
	private interface MethodAnnotatedService {

		@GetExchange
		void performGet();

		@PostExchange(url = "/url", contentType = APPLICATION_JSON_VALUE, accept = APPLICATION_JSON_VALUE)
		void performPost();

	}


	@SuppressWarnings("unused")
	@HttpExchange(url = "/base", contentType = APPLICATION_CBOR_VALUE, accept = APPLICATION_CBOR_VALUE)
	private interface TypeAndMethodAnnotatedService extends MethodAnnotatedService {
	}

}