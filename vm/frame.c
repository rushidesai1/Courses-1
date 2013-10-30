/* Library Inclusion */
#include "vm/frame.h"

#include <debug.h>
#include "lib/round.h"   
#include "threads/thread.h"    
#include "threads/malloc.h"    
#include "threads/palloc.h"    
#include "userprog/process.h"
#include "userprog/pagedir.h"

#include "vm/page.h"
#include "vm/swap.h"

#define PGSIZE 4096

void* fget_page_aux (enum palloc_flags flags, void * vaddr);

/* Define the instantiated hasing function */
static unsigned fte_hash_func (const struct hash_elem *e, void * aux UNUSED) 
{
    struct FTE * f = hash_entry (e, struct FTE, FTE_helem); 
    return hash_bytes (&f->paddr, sizeof(f->paddr));
}

/* Define the instantiated hashing comparison function */
static bool fte_hash_less (const struct hash_elem *a, const struct hash_elem *b,
        void * aux UNUSED) 
{
    struct FTE * fa = hash_entry (a, struct FTE, FTE_helem);
    struct FTE * fb = hash_entry (b, struct FTE, FTE_helem);
    return fa->paddr < fb->paddr;
}

/* initialize both global frame table and its lock */
bool frame_table_init (void) 
{
    lock_init (&frame_table_lock);
    bool success = hash_init (&frame_table, fte_hash_func, fte_hash_less,
            NULL);
    return success;
}

/* find the frame table entry */
struct FTE * frame_table_find (void* paddr) 
{
    // presume nothing found
    struct FTE * finding = NULL;

    // define the target
    struct FTE temp;
    temp.paddr = (void *) ROUND_DOWN ( (int) paddr,  PGSIZE);

    // use lock to protect critical section
    lock_acquire (&frame_table_lock);

    // search the hash table entry
    struct hash_elem * felem = hash_find (&frame_table, &(temp.FTE_helem));
    finding = hash_entry (felem, struct FTE, FTE_helem);
    
    // release the lock to allow other access
    lock_release (&frame_table_lock);

    // return the found structure
    return finding;
}

/* add one new entry to the frame table */
struct FTE * frame_table_put (void *paddr, void *vaddr, struct SP * page)
{
    // create a new_fte frame table entry structure
    struct FTE * new_fte = (struct FTE *) malloc (sizeof (struct FTE));
    new_fte->paddr = paddr;
    new_fte->vaddr = vaddr;
    new_fte->supplementary_page = page;
    new_fte->locked = false;

    // insert that new_fte entry to the global frame table
    struct hash_elem *helem = hash_insert (&frame_table, &new_fte->FTE_helem);

    // return the new_ftely created structure if succeed
    if (helem != NULL) return new_fte;
    else return NULL;
}

/* remove the specified frame table entry */
struct FTE * frame_table_remove (void* paddr) 
{
    // presume nothing removed
    struct FTE * removed = NULL;

    // define the target stucture
    // FIXME: may be problematic
    struct FTE temp;
    temp.paddr = (void *) ROUND_DOWN ((int) paddr, PGSIZE);

    // delete the targeted hash table entry
    struct hash_elem * delem = hash_delete (&frame_table, &temp.FTE_helem);

    
    // return the removed structure
    if (delem != NULL) {
        removed = hash_entry (delem, struct FTE, FTE_helem);
        return removed;
    }
    else return NULL;
}

/* allocate a frame and update the frame table */
void * fget_page (enum palloc_flags flags, void * vaddr) 
{
    // acquire the lock
    lock_acquire (&frame_table_lock);

    struct FTE * fte = fget_page_aux (flags, vaddr);

    // release the lock
    lock_release (&frame_table_lock);

    return fte->paddr;
}

/* Get physical address of a page and lock it */
void * fget_page_lock (enum palloc_flags flags, void * vaddr) 
{
    // lock the whole frame table
    lock_acquire (&frame_table_lock);

    // acquire the page as usual
    struct FTE * f = fget_page_aux (flags, vaddr);
    // after that, we lock frame entry
    f->locked = true;
 
    // release thee frame table lock
    lock_release (&frame_table_lock);
    return f->paddr;
}

/* Rountine abstraction for getting a page */
void* fget_page_aux (enum palloc_flags flags, void * vaddr) 
{
    // make sure the resource in user pool is allocated, rather than kernel's
    ASSERT ((flags & PAL_USER) != 0);

    // physically apply for a memory location
    void * paddr = palloc_get_page (flags);
    if (paddr == NULL) {
        // TODO: add mechanism for page fault, swapping out is required

    }

    // get the page table owned by this process
    struct hash * spt = thread_current()->spt;
    // add new page into that page table
    struct SP * page = sp_table_put (spt, vaddr);

    // update it in the global frame table
    struct FTE * fte = frame_table_put (paddr, vaddr, page);

    return fte==NULL?NULL:paddr;
}

/* free page from frame table */
void ffree_page (void *page)
{
    lock_acquire (&frame_table_lock);
    palloc_free_page (page);

    struct FTE * f = frame_table_remove (page);

    if (f != NULL) 
    {
        free (f);
        // TODO: remove the corresponding supplementary page

    }

    lock_release (&frame_table_lock);
    return ;
}

/* evict the frame to be evicted */
struct FTE * fget_evict (void) 
{

    return NULL;
}

/* clean up all frames and free all relevant resource */
void fcleanup (void) 
{
    lock_acquire (&frame_table_lock);
    // TODO: implement the core

    lock_release (&frame_table_lock);
}

/* set page  */
void fset_page_lock (void) 
{
    return ;
} 
