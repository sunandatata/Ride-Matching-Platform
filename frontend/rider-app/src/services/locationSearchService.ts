import { Location } from '@/types'

const globalFallbackLocations: Location[] = [
  { latitude: 51.5074, longitude: -0.1278, address: 'London, UK' },
  { latitude: 48.8566, longitude: 2.3522, address: 'Paris, France' },
  { latitude: 52.52, longitude: 13.405, address: 'Berlin, Germany' },
  { latitude: 41.9028, longitude: 12.4964, address: 'Rome, Italy' },
  { latitude: 35.6895, longitude: 139.6917, address: 'Tokyo, Japan' },
  { latitude: 1.3521, longitude: 103.8198, address: 'Singapore' },
  { latitude: 25.2048, longitude: 55.2708, address: 'Dubai, UAE' },
  { latitude: -33.8688, longitude: 151.2093, address: 'Sydney, Australia' },
  { latitude: 43.6532, longitude: -79.3832, address: 'Toronto, Canada' },
  { latitude: 19.4326, longitude: -99.1332, address: 'Mexico City, Mexico' },
  { latitude: -26.2041, longitude: 28.0473, address: 'Johannesburg, South Africa' },
  { latitude: 28.6139, longitude: 77.209, address: 'New Delhi, India' },
  { latitude: -23.5505, longitude: -46.6333, address: 'Sao Paulo, Brazil' },
  { latitude: 37.7749, longitude: -122.4194, address: 'San Francisco, CA, USA' },
  { latitude: 34.0522, longitude: -118.2437, address: 'Los Angeles, CA, USA' },
]

const normalizeAddress = (value: string) => value.trim().replace(/\s+/g, ' ')

const scoreFallback = (query: string, location: Location) => {
  const normalizedQuery = query.toLowerCase()
  const normalizedAddress = location.address.toLowerCase()
  if (normalizedAddress.includes(normalizedQuery)) return 0
  if (normalizedQuery.length <= 3) return 1
  return normalizedAddress.startsWith(normalizedQuery) ? 1 : 2
}

const queryNominatim = async (query: string): Promise<Location[]> => {
  const url = `https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=8&q=${encodeURIComponent(query)}`
  const response = await fetch(url, {
    headers: {
      Accept: 'application/json',
      'Accept-Language': 'en-US,en;q=0.9',
    },
  })

  if (!response.ok) {
    throw new Error('Location search failed')
  }

  const data = await response.json() as Array<{
    lat?: string
    lon?: string
    display_name?: string
  }>

  return data
    .map((item) => ({
      latitude: Number(item.lat),
      longitude: Number(item.lon),
      address: item.display_name || query,
    }))
    .filter((location) => Number.isFinite(location.latitude) && Number.isFinite(location.longitude))
}

export const locationSearchService = {
  searchLocations: async (query: string): Promise<Location[]> => {
    const normalizedQuery = normalizeAddress(query)
    if (!normalizedQuery) {
      return globalFallbackLocations.slice(0, 8)
    }

    try {
      const remoteResults = await queryNominatim(normalizedQuery)
      if (remoteResults.length > 0) {
        return remoteResults
      }
    } catch {
      // Fall back to curated global locations below.
    }

    return [...globalFallbackLocations]
      .sort((left, right) => scoreFallback(normalizedQuery, left) - scoreFallback(normalizedQuery, right))
      .slice(0, 8)
  },
  getFallbackLocations: (): Location[] => globalFallbackLocations.slice(0, 8),
}
