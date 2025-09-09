package com.fradantim.sw2tgm.dto.sw;

//{"success":true,"unverified":false,"data":{"username":"fradantim@gmail.com","email":"fradantim@gmail.com","devicePlatform":"web","emailOptOut":false,"unverified":false,"createdAt":"2025-09-06T22:43:37.252Z","updatedAt":"2025-09-06T22:43:57.267Z","athlete":{"__type":"Pointer","className":"TBAthlete","objectId":"GEVcpxw7Oa"},"ACL":{"hXRzxUny1u":{"read":true,"write":true}},"sessionToken":"r:3573089e2bf951dd2b8aca25d4f6e8bb","objectId":"hXRzxUny1u"}}
public record LoginResponse(LoginResponseData data) {
	public record LoginResponseData(String sessionToken, String objectId) {
	}
}