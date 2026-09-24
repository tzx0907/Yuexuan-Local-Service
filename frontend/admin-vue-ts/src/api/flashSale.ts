import request from '@/utils/request'

/** 限时购活动接口。request 已统一以 /api 为前缀，经 Nginx 转发至 /admin。 */
export const getFlashSalePage = (params: any) => request({ url: '/flash-sale/list', method: 'get', params })
export const createFlashSale = (data: any) => request({ url: '/flash-sale', method: 'post', data })
export const updateFlashSale = (data: any) => request({ url: '/flash-sale', method: 'put', data })
export const updateFlashSaleStatus = (id: number | string, status: number) => request({ url: `/flash-sale/${id}/status/${status}`, method: 'put' })
export const searchFlashSaleSkuOptions = (keyword: string) => request({ url: '/flash-sale/sku-options', method: 'get', params: { keyword } })
