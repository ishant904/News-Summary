package com.example.finalnews.Fragments

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finalnews.R
import com.example.finalnews.viewModelsAndFactory.SelectionViewModel
import me.grantland.widget.AutofitTextView

class CategoriesSlideFragment : Fragment() {

    interface OnItemClickListener {
        fun onItemClickCategories(item: String)
    }

    private lateinit var listener: OnItemClickListener
    private val sharedViewModel: SelectionViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as OnItemClickListener
    }

    override fun onCreateView(inflater: LayoutInflater,container: ViewGroup?,savedInstanceState: Bundle?): View {

        val rootView = inflater.inflate(R.layout.fragment_categories_slide_page, container, false) as ViewGroup

        val mRecyclerView: RecyclerView = rootView.findViewById(R.id.recyclerView_categories)
        val numberOfColumns : Int = requireContext().resources.getString(R.string.no_of_cols).toInt()
        //int numberOfColumns = Integer.parseInt(getContext().getResources().getString(R.string.no_of_cols));
        mRecyclerView.layoutManager = GridLayoutManager(context, numberOfColumns)
        mRecyclerView.setHasFixedSize(true)
        val mAdapter = CategoriesAdapter()
        mRecyclerView.adapter = mAdapter

        sharedViewModel.mCategoriesSelectedLv.observe(viewLifecycleOwner) {
            mAdapter.data = it
            Log.d(CategoriesSlideFragment::class.java.name, "categories observer data changed")
        }

        return rootView
    }

    inner class CategoriesAdapter : RecyclerView.Adapter<CategoriesAdapter.CategoriesAdapterViewHolder>() {

        var data:ArrayList<String>? = arrayListOf()

        override fun onCreateViewHolder(parent: ViewGroup,viewType: Int): CategoriesAdapterViewHolder {
            val context = parent.context
            val layoutIdForItem: Int = R.layout.categories_item
            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(layoutIdForItem, parent, false)
            return CategoriesAdapterViewHolder(view)
        }

        override fun onBindViewHolder(holder: CategoriesAdapterViewHolder,position: Int) {
            holder.mCategoryImageView.setImageResource(mThumbIds[position])
            holder.mCategoryTextView.text = mCategories[position]
            holder.bind(mCategories[position], listener, position)
            if (data?.contains(mCategories[position]) == true) {
                holder.mCategoryConstraintLayout.setBackgroundColor(resources.getColor(R.color.colorPrimaryDark))
            } else {
                holder.mCategoryConstraintLayout.setBackgroundColor(Color.TRANSPARENT)
            }
        }

        override fun getItemCount(): Int {
            return mThumbIds.size
        }

        inner class CategoriesAdapterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val mCategoryConstraintLayout : ConstraintLayout = itemView.findViewById(R.id.constraint_layout_categories_item)
            val mCategoryImageView: ImageView = itemView.findViewById(R.id.categories_icon)
            val mCategoryTextView: AutofitTextView = itemView.findViewById(R.id.categories_desc)

            fun bind(mCategory: String,clickListener: OnItemClickListener, position: Int) {
                itemView.setOnClickListener {
                    notifyItemChanged(position)
                    clickListener.onItemClickCategories(mCategory)
                }
            }
        }
    }

    private val mThumbIds = arrayOf(
        R.drawable.business, R.drawable.entertainment, R.drawable.health, R.drawable.science,
        R.drawable.sports, R.drawable.technology
    )
    private val mCategories = arrayOf(
        "Business", "Entertainment", "Health", "Science",
        "Sports", "Technology"
    )
}