#
# Copyright (c) 2023 Airbyte, Inc., all rights reserved.
#


from .report_streams import RecordType, ReportStream

METRICS_MAP = {
    "campaigns": [
        "addToCart",
        "addToCartClicks",
        "addToCartRate",
        "addToCartViews",
        "brandedSearches",
        "brandedSearchesClicks",
        "brandedSearchesViews",
        "brandedSearchRate",
        "campaignBudgetCurrencyCode",
        "campaignId",
        "campaignName",
        "clicks",
        "cost",
        "date",
        "detailPageViews",
        "detailPageViewsClicks",
        "eCPAddToCart",
        "eCPBrandSearch",
        "impressions",
        "impressionsViews",
        "leadFormOpens",
        "leads",
        "linkOuts",
        "newToBrandPurchases",
        "newToBrandPurchasesClicks",
        "newToBrandSalesClicks",
        "newToBrandUnitsSold",
        "newToBrandUnitsSoldClicks",
        "purchases",
        "purchasesClicks",
        "purchasesPromotedClicks",
        "sales",
        "salesClicks",
        "salesPromotedClicks",
        "unitsSold",
        "unitsSoldClicks",
        "videoCompleteViews",
        "videoFirstQuartileViews",
        "videoMidpointViews",
        "videoThirdQuartileViews",
        "videoUnmutes",
        "viewabilityRate",
        "viewClickThroughRate",
        "campaignBudgetAmount",
        "campaignStatus",
        "costType",
        "cumulativeReach",
        "impressionsFrequencyAverage",
        "newToBrandDetailPageViewClicks",
        "newToBrandDetailPageViewRate",
        "newToBrandDetailPageViews",
        "newToBrandDetailPageViewViews",
        "newToBrandECPDetailPageView",
        "newToBrandSales",
    ],
}

METRICS_TYPE_TO_ID_MAP = {"campaigns": "campaignId", "adGroups": "adGroupId", "productAds": "adId", "targets": "targetId", "asins": "asin"}



class SponsoredDisplayReportStream(ReportStream):
    """
    https://advertising.amazon.com/API/docs/en-us/sponsored-display/3-0/openapi#/Reports
    """
    REPORT_DATE_FORMAT = "YYYY-MM-DD"
    API_VERSION = "reporting"  # v3
    def report_init_endpoint(self, record_type: str) -> str:
        return f"/{self.API_VERSION}/reports"

    metrics_map = METRICS_MAP
    metrics_type_to_id_map = METRICS_TYPE_TO_ID_MAP

    def _get_init_report_body(self, start_date: str, end_date: str, record_type: str, profile):
        data = {
            "name": f"{record_type} {profile.profileId} report {start_date} to {end_date}",
            "startDate": start_date,
            "endDate": end_date,
            "configuration": {
                "adProduct": "SPONSORED_DISPLAY",
                "groupBy": ["campaign"],
                "columns": METRICS_MAP["campaigns"],
                "reportTypeId": "sdCampaigns",
                "filters": [],
                "timeUnit": "DAILY",
                "format": "GZIP_JSON",
            },
        }

        print(f'xk-debug-display: {data}')

        yield data
