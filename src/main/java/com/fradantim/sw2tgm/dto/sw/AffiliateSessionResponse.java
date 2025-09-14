package com.fradantim.sw2tgm.dto.sw;

import java.util.List;

public record AffiliateSessionResponse(Data data) {
	public record Data(Athlete athlete) { }
	public record Athlete(List<Subscription> subscriptions) { }
	public record Subscription(MarketplaceProduct marketplaceProduct) { }
	public record MarketplaceProduct(List<PublishingTrack> publishingTracks) { }
	public record PublishingTrack(String key) { }
}