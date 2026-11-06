import { getResult } from '@/global/api/http'
import type { MenuNode } from './types'

export function fetchMyMenus(): Promise<MenuNode[]> {
  return getResult<MenuNode[]>('/api/v1/menus/me')
}
