import React from 'react'
import { Card, CardContent, Typography, Box, Skeleton } from '@mui/material'

interface MetricCardProps {
  title: string
  value: string | number
  subtitle?: string
  icon?: React.ReactNode
  isLoading?: boolean
  tone?: 'blue' | 'green' | 'amber' | 'violet' | 'slate'
}

const toneStyles = {
  blue: { bg: '#EFF6FF', fg: '#1D4ED8' },
  green: { bg: '#ECFDF3', fg: '#15803D' },
  amber: { bg: '#FFFBEB', fg: '#B45309' },
  violet: { bg: '#F5F3FF', fg: '#6D28D9' },
  slate: { bg: '#F1F5F9', fg: '#334155' },
}

export const MetricCard: React.FC<MetricCardProps> = ({
  title,
  value,
  subtitle,
  icon,
  isLoading = false,
  tone = 'blue',
}) => {
  const colors = toneStyles[tone]

  return (
    <Card
      sx={{
        borderRadius: 2,
        boxShadow: '0 12px 30px rgba(15, 23, 42, 0.08)',
        border: '1px solid #E2E8F0',
        height: '100%',
      }}
    >
      <CardContent sx={{ p: 2.25 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2 }}>
          <Box sx={{ minWidth: 0 }}>
            <Typography sx={{ color: '#64748B', fontSize: 13, fontWeight: 800 }}>
              {title}
            </Typography>
            {isLoading ? (
              <Skeleton width={100} height={34} sx={{ mt: 1 }} />
            ) : (
              <Typography variant="h5" sx={{ fontWeight: 900, mt: 0.75, color: '#0F172A', letterSpacing: 0 }}>
                {value}
              </Typography>
            )}
            {subtitle && (
              <Typography variant="caption" sx={{ mt: 0.75, display: 'block', color: '#94A3B8', fontWeight: 700 }}>
                {subtitle}
              </Typography>
            )}
          </Box>
          {icon && (
            <Box sx={{
              width: 42,
              height: 42,
              borderRadius: 2,
              display: 'grid',
              placeItems: 'center',
              bgcolor: colors.bg,
              color: colors.fg,
              flex: '0 0 auto',
              '& svg': { fontSize: 24 },
            }}>
              {icon}
            </Box>
          )}
        </Box>
      </CardContent>
    </Card>
  )
}
