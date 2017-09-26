/*
 * Copyright (C) 2015 SoftIndex LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.datakernel.dns;

import io.datakernel.async.Stages;
import io.datakernel.time.CurrentTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static io.datakernel.dns.DnsMessage.AAAA_RECORD_TYPE;
import static io.datakernel.dns.DnsMessage.A_RECORD_TYPE;

/**
 * Represents a cache for storing resolved domains during its time to live.
 */
final class DnsCache {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	private final Map<String, CachedDnsLookupResult> cache = new ConcurrentHashMap<>();
	private final Map<Long, Set<String>> expirations = new HashMap<>();
	private long lastCleanupSecond;

	private final long errorCacheExpirationSeconds;
	private final long hardExpirationDeltaSeconds;
	private final CurrentTimeProvider timeProvider;

	private long maxTtlSeconds = Long.MAX_VALUE;
	private final long timedOutExceptionTtlSeconds;

	private AsyncDnsClient.Inspector inspector;

	/**
	 * Enum with freshness cache's entry.
	 * <ul>
	 * <li>FRESH - while time to live of this entry has not passed, empty is considered resolved
	 * <li> SOFT_TTL_EXPIRED - while hard time expiration has not passed, empty is considered resolved, but needs refreshing
	 * <li> HARD_TTL_EXPIRED - while hard time expiration has passed, empty is considered not resolved
	 * </ul>
	 */
	public enum DnsCacheEntryFreshness {
		FRESH,
		SOFT_TTL_EXPIRED,
		HARD_TTL_EXPIRED,
	}

	public enum DnsCacheQueryResult {
		RESOLVED,
		RESOLVED_NEEDS_REFRESHING,
		NOT_RESOLVED
	}
	/**
	 * Creates a new DNS cache.
	 *
	 * @param timeProvider               time provider
	 * @param errorCacheExpirationMillis expiration time for errors without time to live
	 * @param hardExpirationDeltaMillis  delta between time at which entry is considered resolved, but needs
	 * @param timedOutExceptionTtlMillis expiration time for timed out exception
	 */
	private DnsCache(CurrentTimeProvider timeProvider, long errorCacheExpirationMillis, long hardExpirationDeltaMillis,
	                 long timedOutExceptionTtlMillis, AsyncDnsClient.Inspector inspector) {
		this.errorCacheExpirationSeconds = errorCacheExpirationMillis / 1000;
		this.hardExpirationDeltaSeconds = hardExpirationDeltaMillis / 1000;
		this.timeProvider = timeProvider;
		this.timedOutExceptionTtlSeconds = timedOutExceptionTtlMillis / 1000;
		this.lastCleanupSecond = getCurrentSecond();
		this.inspector = inspector;
	}

	public static DnsCache create(CurrentTimeProvider timeProvider, long errorCacheExpirationMillis,
	                              long hardExpirationDeltaMillis, long timedOutExceptionTtl, AsyncDnsClient.Inspector inspector) {
		return new DnsCache(timeProvider, errorCacheExpirationMillis, hardExpirationDeltaMillis, timedOutExceptionTtl, inspector);
	}

	private boolean isRequestedType(CachedDnsLookupResult cachedResult, boolean requestedIpv6) {
		Short cachedResultType = cachedResult.getType();

		if (cachedResultType == A_RECORD_TYPE & !requestedIpv6)
			return true;

		return cachedResultType == AAAA_RECORD_TYPE & requestedIpv6;
	}

	/**
	 * Tries to get status of the entry for some domain name from the cache.
	 *
	 * @param domainName domain name for finding entry
	 * @param ipv6       type of result, if true - IPv6, false - IPv4
	 * @return DnsCacheQueryResult for this domain name
	 */

	public DnsCacheResultStage<InetAddress[]> tryToResolve(String domainName, boolean ipv6) {
		CachedDnsLookupResult cachedResult = cache.get(domainName);

		if (cachedResult == null) {
			if (logger.isDebugEnabled())
				logger.debug("Cache miss for host: {}", domainName);
			return new DnsCacheResultStage<>(null, DnsCacheQueryResult.NOT_RESOLVED);
		}

		if (cachedResult.isSuccessful() && !isRequestedType(cachedResult, ipv6)) {
			if (logger.isDebugEnabled())
				logger.debug("Cache miss for host: {}", domainName);
			return new DnsCacheResultStage<>(null, DnsCacheQueryResult.NOT_RESOLVED);
		}

		DnsCacheEntryFreshness freshness = getResultFreshness(cachedResult);

		switch (freshness) {
			case HARD_TTL_EXPIRED: {
				if (logger.isDebugEnabled())
					logger.debug("Hard TTL expired for host: {}", domainName);
				return new DnsCacheResultStage<>(null, DnsCacheQueryResult.NOT_RESOLVED);
			}

			case SOFT_TTL_EXPIRED: {
				if (logger.isDebugEnabled())
					logger.debug("Soft TTL expired for host: {}", domainName);
				final CompletionStage<InetAddress[]> stage = returnResultThroughStage(domainName, cachedResult);
				return new DnsCacheResultStage<>(stage, DnsCacheQueryResult.RESOLVED_NEEDS_REFRESHING);
			}

			default: {
				final CompletionStage<InetAddress[]> stage = returnResultThroughStage(domainName, cachedResult);
				return new DnsCacheResultStage<>(stage, DnsCacheQueryResult.RESOLVED);
			}
		}
	}

	private CompletionStage<InetAddress[]> returnResultThroughStage(String domainName, CachedDnsLookupResult result) {
		if (result.isSuccessful()) {
			InetAddress[] ipsFromCache = result.getIps();
			if (logger.isDebugEnabled()) logger.debug("Cache hit for host: {}", domainName);
			if (inspector != null) inspector.onCacheHit(domainName, ipsFromCache);
			return Stages.of(ipsFromCache);
		} else {
			DnsException exception = result.getException();
			if (logger.isDebugEnabled()) logger.debug("Error cache hit for host: {}", domainName);
			if (inspector != null) inspector.onCacheHitError(domainName, exception);
			return Stages.ofException(exception);
		}
	}

	private DnsCacheEntryFreshness getResultFreshness(CachedDnsLookupResult result) {
		long softExpirationSecond = result.getExpirationSecond();
		long hardExpirationSecond = getHardExpirationSecond(softExpirationSecond);
		long currentSecond = getCurrentSecond();

		if (currentSecond >= hardExpirationSecond)
			return DnsCacheEntryFreshness.HARD_TTL_EXPIRED;
		else if (currentSecond >= softExpirationSecond)
			return DnsCacheEntryFreshness.SOFT_TTL_EXPIRED;
		else
			return DnsCacheEntryFreshness.FRESH;
	}

	/**
	 * Adds DnsQueryResult to this cache
	 *
	 * @param result result to add
	 */
	public void add(DnsQueryResult result) {
		if (result.getMinTtl() == 0)
			return;
		long expirationSecond;
		if (result.getMinTtl() > maxTtlSeconds)
			expirationSecond = maxTtlSeconds + getCurrentSecond();
		else
			expirationSecond = result.getMinTtl() + getCurrentSecond();
		String domainName = result.getDomainName();
		cache.put(domainName, CachedDnsLookupResult.fromQueryWithExpiration(result, expirationSecond));
		setExpiration(expirations, expirationSecond + hardExpirationDeltaSeconds, domainName);
		if (logger.isDebugEnabled())
			logger.debug("Add result to cache for host: {}", domainName);
	}

	/**
	 * Adds DnsException to this cache
	 *
	 * @param exception exception to add
	 */
	public void add(DnsException exception) {
		long expirationSecond = errorCacheExpirationSeconds + getCurrentSecond();
		if (exception.getErrorCode() == DnsMessage.ResponseErrorCode.TIMED_OUT) {
			expirationSecond = timedOutExceptionTtlSeconds + getCurrentSecond();
		}
		String domainName = exception.getDomainName();
		cache.put(domainName, CachedDnsLookupResult.fromExceptionWithExpiration(exception, expirationSecond));
		setExpiration(expirations, expirationSecond + hardExpirationDeltaSeconds, domainName);
		if (logger.isDebugEnabled())
			logger.debug("Add exception to cache for host: {}", domainName);
	}

	public void performCleanup() {
		long callSecond = getCurrentSecond();

		if (callSecond > lastCleanupSecond) {
			clear(callSecond, lastCleanupSecond);
			lastCleanupSecond = callSecond;
		}
	}

	private void clear(long callSecond, long lastCleanupSecond) {
		for (long i = lastCleanupSecond; i <= callSecond; ++i) {
			Collection<String> domainNames = expirations.remove(i);

			if (domainNames != null) {
				for (String domainName : domainNames) {
					CachedDnsLookupResult cachedResult = cache.get(domainName);
					if (cachedResult != null && getResultFreshness(cachedResult) == DnsCacheEntryFreshness.HARD_TTL_EXPIRED) {
						if (inspector != null) inspector.onDomainExpired(domainName);
						cache.remove(domainName);
					}
				}
			}
		}
	}

	public long getMaxTtlSeconds() {
		return maxTtlSeconds;
	}

	public void setMaxTtlSeconds(long maxTtlSeconds) {
		this.maxTtlSeconds = maxTtlSeconds;
	}

	public long getTimedOutExceptionTtlSeconds() {
		return timedOutExceptionTtlSeconds;
	}

	public void clear() {
		cache.clear();
		expirations.clear();
	}

	private long getCurrentSecond() {
		return timeProvider.currentTimeMillis() / 1000;
	}

	private long getHardExpirationSecond(long softExpirationSecond) {
		return softExpirationSecond + hardExpirationDeltaSeconds;
	}

	public int getNumberOfCachedDomainNames() {
		return cache.size();
	}

	public int getNumberOfCachedExceptions() {
		int exceptions = 0;

		for (CachedDnsLookupResult cachedResult : cache.values()) {
			if (!cachedResult.isSuccessful())
				++exceptions;
		}

		return exceptions;
	}

	public String[] getSuccessfullyResolvedDomainNames() {
		List<String> domainNames = new ArrayList<>();

		for (Map.Entry<String, CachedDnsLookupResult> entry : cache.entrySet()) {
			if (entry.getValue().isSuccessful()) {
				domainNames.add(entry.getKey());
			}
		}

		return domainNames.toArray(new String[domainNames.size()]);
	}

	public String[] getDomainNamesOfFailedRequests() {
		List<String> domainNames = new ArrayList<>();

		for (Map.Entry<String, CachedDnsLookupResult> entry : cache.entrySet()) {
			if (!entry.getValue().isSuccessful()) {
				domainNames.add(entry.getKey());
			}
		}

		return domainNames.toArray(new String[domainNames.size()]);
	}

	public String[] getAllCacheEntriesWithHeaderLine() {
		List<String> cacheEntries = new ArrayList<>();
		StringBuilder sb = new StringBuilder();

		if (!cache.isEmpty())
			cacheEntries.add("domainName;ips;secondsToSoftExpiration;secondsToHardExpiration;status");

		for (Map.Entry<String, CachedDnsLookupResult> detailedCacheEntry : cache.entrySet()) {
			String domainName = detailedCacheEntry.getKey();
			CachedDnsLookupResult dnsLookupResult = detailedCacheEntry.getValue();
			InetAddress[] ips = dnsLookupResult.getIps();
			long softExpirationSecond = dnsLookupResult.getExpirationSecond();
			long hardExpirationSecond = getHardExpirationSecond(softExpirationSecond);
			long currentSecond = getCurrentSecond();
			long secondsToSoftExpiration = softExpirationSecond - currentSecond;
			long secondsToHardExpiration = hardExpirationSecond - currentSecond;
			sb.append(domainName);
			sb.append(";");
			sb.append(Arrays.toString(ips));
			sb.append(";");
			sb.append(secondsToSoftExpiration <= 0 ? "expired" : secondsToSoftExpiration);
			sb.append(";");
			sb.append(secondsToHardExpiration <= 0 ? "expired" : secondsToHardExpiration);
			sb.append(";");
			sb.append(dnsLookupResult.getErrorCode());
			cacheEntries.add(sb.toString());
			sb.setLength(0);
		}

		return cacheEntries.toArray(new String[cacheEntries.size()]);
	}

	private void setExpiration(Map<Long, Set<String>> expirations, long time, String domain) {
		Set<String> sameTime = expirations.get(time);
		if (sameTime == null) {
			sameTime = new HashSet<>();
			expirations.put(time, sameTime);
		}
		sameTime.add(domain);
	}

	void setInspector(AsyncDnsClient.Inspector inspector) {
		this.inspector = inspector;
	}

	public static class DnsCacheResultStage<T> {
		private final CompletionStage<T> stage;
		private final DnsCacheQueryResult dnsCacheResult;

		public DnsCacheResultStage(CompletionStage<T> stage, DnsCacheQueryResult dnsCacheResult) {
			this.stage = stage;
			this.dnsCacheResult = dnsCacheResult;
		}

		public CompletionStage<T> getStage() {
			return stage;
		}

		public DnsCacheQueryResult getDnsCacheResult() {
			return dnsCacheResult;
		}

		public boolean isStageEnable() {
			return stage != null;
		}
	}
}
