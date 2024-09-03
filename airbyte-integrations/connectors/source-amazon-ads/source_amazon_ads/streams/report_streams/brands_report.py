#
# Copyright (c) 2023 Airbyte, Inc., all rights reserved.
#

from http import HTTPStatus

from .products_report import SponsoredProductsReportStream
from .report_streams import ReportStream

METRICS_MAP = {
    "campaigns": [
        "addToCart",
        "addToCartClicks",
        "addToCartRate",
        "brandedSearches",
        "brandedSearchesClicks",
        "campaignBudgetAmount",
        "campaignBudgetCurrencyCode",
        "campaignBudgetType",
        "campaignId",
        "campaignName",
        "campaignStatus",
        "clicks",
        "cost",
        "costType",
        "date",
        "detailPageViews",
        "detailPageViewsClicks",
        "eCPAddToCart",
        "impressions",
        "newToBrandDetailPageViewRate",
        "newToBrandDetailPageViews",
        "newToBrandDetailPageViewsClicks",
        "newToBrandECPDetailPageView",
        "newToBrandPurchases",
        "newToBrandPurchasesClicks",
        "newToBrandPurchasesPercentage",
        "newToBrandPurchasesRate",
        "newToBrandSales",
        "newToBrandSalesClicks",
        "newToBrandSalesPercentage",
        "newToBrandUnitsSold",
        "newToBrandUnitsSoldClicks",
        "newToBrandUnitsSoldPercentage",
        "purchases",
        "purchasesClicks",
        "purchasesPromoted",
        "sales",
        "salesClicks",
        "salesPromoted",
        "topOfSearchImpressionShare",
        "unitsSold",
        "unitsSoldClicks",
        "video5SecondViewRate",
        "video5SecondViews",
        "videoCompleteViews",
        "videoFirstQuartileViews",
        "videoMidpointViews",
        "videoThirdQuartileViews",
        "videoUnmutes",
        "viewabilityRate",
        "viewableImpressions",
        "viewClickThroughRate",
    ],
}

METRICS_TYPE_TO_ID_MAP = {
    "keywords": "keywordBid",
    "adGroups": "adGroupId",
    "campaigns": "campaignId",
}


class SponsoredBrandsReportStream(ReportStream):
    """
    https://advertising.amazon.com/API/docs/en-us/reference/sponsored-brands/2/reports
    """

    def report_init_endpoint(self, record_type: str) -> str:
        return f"/v2/hsa/{record_type}/report"

    metrics_map = METRICS_MAP
    metrics_type_to_id_map = METRICS_TYPE_TO_ID_MAP

    def _get_init_report_body(self, start_date: str, report_date: str, record_type: str, profile):
        metrics_list = self.metrics_map[record_type]
        body = {
            "reportDate": report_date,
        }
        yield {**body, "metrics": ",".join(metrics_list)}


METRICS_MAP_V3 = {
    "purchasedAsin": [
        "campaignBudgetCurrencyCode",
        "campaignName",
        "adGroupName",
        "attributionType",
        "purchasedAsin",
        "productName",
        "productCategory",
        "sales14d",
        "orders14d",
        "unitsSold14d",
        "newToBrandSales14d",
        "newToBrandPurchases14d",
        "newToBrandUnitsSold14d",
        "newToBrandSalesPercentage14d",
        "newToBrandPurchasesPercentage14d",
        "newToBrandUnitsSoldPercentage14d",
    ]
}

METRICS_TYPE_TO_ID_MAP_V3 = {
    "campaigns": "campaignId",
}


class SponsoredBrandsV3ReportStream(SponsoredProductsReportStream):
    """
    https://advertising.amazon.com/API/docs/en-us/guides/reporting/v3/report-types#purchased-product-reports
    """

    API_VERSION = "reporting"  # v3
    REPORT_DATE_FORMAT = "YYYY-MM-DD"
    ad_product = "SPONSORED_BRANDS"
    report_is_created = HTTPStatus.OK
    metrics_map = METRICS_MAP
    metrics_type_to_id_map = METRICS_TYPE_TO_ID_MAP_V3

    def _get_init_report_body(self, start_date: str, end_date: str, record_type: str, profile):
        metrics_list = self.metrics_map[record_type]

        reportTypeId = "sbCampaigns"
        group_by = ["campaign"]

        body = {
            "name": f"{record_type} {profile.profileId} report {start_date} to {end_date}",
            "startDate": start_date,
            "endDate": end_date,
            "configuration": {
                "adProduct": self.ad_product,
                "groupBy": group_by,
                "columns": metrics_list,
                "reportTypeId": reportTypeId,
                "filters": [],
                "timeUnit": "DAILY",
                "format": "GZIP_JSON",
            },
        }

        print(f"xk-debug-brands: {body}")
        yield body
