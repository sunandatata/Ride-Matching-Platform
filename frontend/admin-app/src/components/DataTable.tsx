import React, { useMemo, useState } from 'react'
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  TablePagination,
  CircularProgress,
  Box,
  Typography,
  TextField,
  InputAdornment,
  MenuItem,
  TableSortLabel,
} from '@mui/material'
import { Search as SearchIcon } from '@mui/icons-material'

type SortDirection = 'asc' | 'desc'

interface Column {
  id: string
  label: string
  minWidth?: number
  align?: 'left' | 'right' | 'center'
  render?: (row: any) => React.ReactNode
  sortable?: boolean
  searchAccessor?: (row: any) => string
  sortAccessor?: (row: any) => string | number
}

interface TableFilter {
  id: string
  label: string
  options: Array<{ value: string; label: string }>
  getValue: (row: any) => string
}

interface DataTableProps {
  columns: Column[]
  rows: any[]
  isLoading?: boolean
  page?: number
  rowsPerPage?: number
  total?: number
  onPageChange?: (page: number) => void
  onRowsPerPageChange?: (rowsPerPage: number) => void
  searchPlaceholder?: string
  filters?: TableFilter[]
}

const valueToSearch = (value: unknown): string => {
  if (value == null) return ''
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return String(value)
  if (typeof value === 'object') return Object.values(value as Record<string, unknown>).map(valueToSearch).join(' ')
  return ''
}

export const DataTable: React.FC<DataTableProps> = ({
  columns,
  rows,
  isLoading = false,
  page = 0,
  rowsPerPage = 10,
  total = 0,
  onPageChange,
  onRowsPerPageChange,
  searchPlaceholder = 'Search table',
  filters = [],
}) => {
  const [search, setSearch] = useState('')
  const [filterValues, setFilterValues] = useState<Record<string, string>>({})
  const [orderBy, setOrderBy] = useState<string>('')
  const [order, setOrder] = useState<SortDirection>('asc')

  const handleChangePage = (_event: unknown, newPage: number) => {
    onPageChange?.(newPage)
  }

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    onRowsPerPageChange?.(parseInt(event.target.value, 10))
    onPageChange?.(0)
  }

  const handleSort = (columnId: string) => {
    const isSameColumn = orderBy === columnId
    setOrder(isSameColumn && order === 'asc' ? 'desc' : 'asc')
    setOrderBy(columnId)
  }

  const visibleRows = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase()
    const activeFilters = Object.entries(filterValues).filter(([, value]) => value)

    const filtered = rows.filter((row) => {
      const matchesSearch = !normalizedSearch || columns.some((column) => {
        if (column.id === 'actions') return false
        const text = column.searchAccessor ? column.searchAccessor(row) : valueToSearch(row[column.id])
        return text.toLowerCase().includes(normalizedSearch)
      })

      const matchesFilters = activeFilters.every(([filterId, value]) => {
        const filter = filters.find((entry) => entry.id === filterId)
        return !filter || filter.getValue(row) === value
      })

      return matchesSearch && matchesFilters
    })

    if (!orderBy) return filtered

    const column = columns.find((entry) => entry.id === orderBy)
    if (!column) return filtered

    return [...filtered].sort((a, b) => {
      const aValue = column.sortAccessor ? column.sortAccessor(a) : valueToSearch(a[column.id])
      const bValue = column.sortAccessor ? column.sortAccessor(b) : valueToSearch(b[column.id])
      const comparison = typeof aValue === 'number' && typeof bValue === 'number'
        ? aValue - bValue
        : String(aValue).localeCompare(String(bValue), undefined, { numeric: true, sensitivity: 'base' })
      return order === 'asc' ? comparison : -comparison
    })
  }, [columns, filterValues, filters, order, orderBy, rows, search])

  return (
    <Paper
      elevation={0}
      sx={{
        borderRadius: 2,
        overflow: 'hidden',
        border: '1px solid #E2E8F0',
        boxShadow: '0 12px 30px rgba(15, 23, 42, 0.06)',
      }}
    >
      <Box sx={{
        p: 2,
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', md: filters.length ? `minmax(260px, 1fr) repeat(${filters.length}, minmax(160px, 220px))` : '1fr' },
        gap: 1.5,
        alignItems: 'center',
        bgcolor: '#FFFFFF',
        borderBottom: '1px solid #E2E8F0',
      }}>
        <TextField
          size="small"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder={searchPlaceholder}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          }}
        />
        {filters.map((filter) => (
          <TextField
            key={filter.id}
            select
            size="small"
            label={filter.label}
            value={filterValues[filter.id] || ''}
            onChange={(event) => setFilterValues((current) => ({ ...current, [filter.id]: event.target.value }))}
          >
            <MenuItem value="">All</MenuItem>
            {filter.options.map((option) => (
              <MenuItem key={option.value} value={option.value}>
                {option.label}
              </MenuItem>
            ))}
          </TextField>
        ))}
      </Box>

      {isLoading && rows.length === 0 ? (
        <Box display="flex" justifyContent="center" alignItems="center" minHeight={360}>
          <CircularProgress />
        </Box>
      ) : visibleRows.length === 0 ? (
        <Box display="flex" flexDirection="column" gap={1} justifyContent="center" alignItems="center" minHeight={320}>
          <Typography sx={{ color: '#0F172A', fontWeight: 900 }}>No matching records</Typography>
          <Typography sx={{ color: '#64748B', fontSize: 14 }}>Adjust search or filters to broaden the results.</Typography>
        </Box>
      ) : (
        <>
          <TableContainer sx={{ maxHeight: 620 }}>
            <Table stickyHeader>
              <TableHead>
                <TableRow>
                  {columns.map((column) => (
                    <TableCell
                      key={column.id}
                      align={column.align || 'left'}
                      sx={{
                        minWidth: column.minWidth,
                        fontWeight: 900,
                        color: '#475569',
                        bgcolor: '#F8FAFC',
                        borderBottom: '1px solid #E2E8F0',
                        fontSize: 12,
                        textTransform: 'uppercase',
                        letterSpacing: 0.4,
                      }}
                    >
                      {column.sortable === false || column.id === 'actions' ? (
                        column.label
                      ) : (
                        <TableSortLabel
                          active={orderBy === column.id}
                          direction={orderBy === column.id ? order : 'asc'}
                          onClick={() => handleSort(column.id)}
                        >
                          {column.label}
                        </TableSortLabel>
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {visibleRows.map((row, index) => (
                  <TableRow
                    key={row.id || index}
                    sx={{
                      '&:hover': { backgroundColor: '#F8FAFC' },
                      '&:last-child td, &:last-child th': { border: 0 },
                    }}
                  >
                    {columns.map((column) => (
                      <TableCell key={column.id} align={column.align || 'left'} sx={{ borderColor: '#EEF2F7', color: '#0F172A' }}>
                        {column.render ? column.render(row) : row[column.id]}
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          {total > rowsPerPage && (
            <TablePagination
              rowsPerPageOptions={[5, 10, 25, 50]}
              component="div"
              count={total}
              rowsPerPage={rowsPerPage}
              page={page}
              onPageChange={handleChangePage}
              onRowsPerPageChange={handleChangeRowsPerPage}
            />
          )}
        </>
      )}
    </Paper>
  )
}
